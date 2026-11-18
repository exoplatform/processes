<!--
Copyright (C) 2026 eXo Platform SAS.
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.
You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
<template>
  <div v-if="components.length" :id="hookId">
    <component
      v-for="component in components"
      :key="component.componentOptions.id"
      :is="component.componentOptions.vueComponent"
      ref="sections"
      v-bind="params"
      class="processes-extension-section" />
  </div>
</template>

<script>
/**
 * Renders the sections other add-ons register on an extension point of a
 * processes drawer, and calls their methods for the drawer: each section may
 * expose validate(), values(), hasChanges(), saved(), markRefused(fieldKey,
 * code), reset(), fields() and fillState(). A section that lacks a method is
 * skipped for it. Emits changed with true once a section holds a change,
 * false when none does, and fill with the sections' fill state each time it
 * changes.
 */
export default {
  props: {
    name: {
      type: String,
      required: true,
    },
    type: {
      type: String,
      required: true,
    },
    params: {
      type: Object,
      default: () => ({}),
    },
    hookId: {
      type: String,
      default: null,
    },
  },
  data: () => ({components: []}),
  watch: {
    components() {
      // The sections render on the next flush, and only then have instances to watch
      this.$nextTick(this.watchSections);
    },
  },
  created() {
    // Not reactive: the section instances already watched, and the latest registration of each id
    this.watched = new WeakSet();
    this.latestRegistrations = new Map();
    document.addEventListener(this.eventName(), this.addRegistered);
    extensionRegistry.loadComponents(this.name)
      .filter(component => component.componentName === this.type)
      .forEach(component => this.addComponent(component));
  },
  beforeDestroy() {
    document.removeEventListener(this.eventName(), this.addRegistered);
  },
  methods: {
    /** @returns {String} the event the extension registry sends when a component registers on this extension point */
    eventName() {
      return `component-${this.name}-${this.type}-updated`;
    },
    /**
     * Adds the section a registration event carries, when it is of this
     * extension point's type.
     * @param {CustomEvent} event the registration event
     * @returns {void}
     */
    addRegistered(event) {
      if (event?.detail?.componentName === this.type) {
        this.addComponent(event.detail);
      }
    },
    /**
     * Adds a registered section, unless its isEnabled, given the params,
     * answers false, or a later registration of its id superseded it while
     * isEnabled was answering. Its init option is not read.
     * @param {Object} component the registered component
     * @returns {Promise<void>} settled once the section is added or skipped
     */
    async addComponent(component) {
      const id = component.componentOptions.id;
      this.latestRegistrations.set(id, component);
      const isEnabled = component.componentOptions.isEnabled;
      if (isEnabled && !(await Promise.resolve(isEnabled(this.params)))) {
        return;
      }
      if (this.latestRegistrations.get(id) !== component) {
        return;
      }
      const components = this.components.filter(existing => existing.componentOptions.id !== id);
      components.push(component);
      components.sort((first, second) => (first.componentOptions.rank || 0) - (second.componentOptions.rank || 0));
      this.components = components;
    },
    /** @returns {Array} the rendered section instances */
    sections() {
      return this.$refs.sections || [];
    },
    /**
     * Tells whether one section holds a change: its hasChanges(), or a
     * non-empty fields() for a section that has no hasChanges().
     * @param {Object} section a section instance
     * @returns {Boolean} true when the section holds a change
     */
    sectionChanged(section) {
      if (typeof section.hasChanges === 'function') {
        return !!section.hasChanges();
      }
      return typeof section.fields === 'function' && Object.keys(section.fields() || {}).length > 0;
    },
    /**
     * Watches each new section's change state, emitting changed, and its fill
     * state when it reports one, emitting fill with every section's state at
     * once, also when it first shows. The getters read the section's reactive
     * data through its methods, so the watchers fire on its own input.
     * @returns {void}
     */
    watchSections() {
      this.sections().filter(section => !this.watched.has(section)).forEach(section => {
        this.watched.add(section);
        this.$watch(() => this.sectionChanged(section), () => this.$emit('changed', this.hasChanges()));
        if (typeof section.fillState === 'function') {
          this.$watch(() => JSON.stringify(section.fillState()), () => this.$emit('fill', this.fillState()), {immediate: true});
        }
      });
    },
    /** @returns {Object} the sections' fill states added up: {total, filled, requiredTotal, requiredFilled, missing}; a section without required counts gives all its fields as required */
    fillState() {
      return this.call('fillState').filter(Boolean).reduce((sum, fill) => ({
        total: sum.total + (fill.total || 0),
        filled: sum.filled + (fill.filled || 0),
        requiredTotal: sum.requiredTotal + (fill.requiredTotal ?? fill.total ?? 0),
        requiredFilled: sum.requiredFilled + (fill.requiredFilled ?? fill.filled ?? 0),
        missing: sum.missing.concat(fill.missing || []),
      }), {total: 0, filled: 0, requiredTotal: 0, requiredFilled: 0, missing: []});
    },
    /**
     * Calls a method on every section that has it.
     * @param {String} method the method's name
     * @param {...*} args its arguments
     * @returns {Array} what each section answered
     */
    call(method, ...args) {
      return this.sections()
        .filter(section => typeof section[method] === 'function')
        .map(section => section[method](...args));
    },
    /** @returns {Promise<Boolean>} true when every section's check passes */
    validate() {
      return Promise.all(this.call('validate')).then(results => results.every(Boolean));
    },
    /** @returns {Object} the sections' values, form id to field key to values */
    values() {
      return Object.assign({}, ...this.call('values'));
    },
    /** @returns {Object} the fields the sections add to the process save */
    fields() {
      return Object.assign({}, ...this.call('fields'));
    },
    /** @returns {Boolean} true when a section holds a change */
    hasChanges() {
      return this.sections().some(this.sectionChanged);
    },
    /** @returns {void} once the sections are told that the values they last gave were saved */
    saved() {
      this.call('saved');
    },
    /**
     * Shows a field the server refused: in the form it names, else in every section holding its key.
     * @param {String} fieldKey the refused field's key
     * @param {String} code the refusal's code
     * @param {String} [formId] the refused field's form, when the refusal names it
     * @returns {void}
     */
    markRefused(fieldKey, code, formId) {
      this.call('markRefused', fieldKey, code, formId);
    },
    /** @returns {void} once the sections forgot what was typed */
    reset() {
      this.call('reset');
    },
  },
};
</script>
