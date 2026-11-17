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
 * code), reset() and fields(). A section that lacks a method is skipped for
 * it. Emits changed with true once a section holds a change, false when none
 * does.
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
  data: () => ({
    components: [],
  }),
  watch: {
    components() {
      // The sections render on the next flush, and only then have instances to watch
      this.$nextTick(this.watchSections);
    },
  },
  created() {
    // Not reactive: the section instances already watched
    this.watched = new WeakSet();
    document.addEventListener(this.eventName(), this.addRegistered);
    extensionRegistry.loadComponents(this.name)
      .filter(component => component.componentName === this.type)
      .forEach(component => this.addComponent(component));
  },
  beforeDestroy() {
    document.removeEventListener(this.eventName(), this.addRegistered);
  },
  methods: {
    eventName() {
      return `component-${this.name}-${this.type}-updated`;
    },
    addRegistered(event) {
      if (event?.detail?.componentName === this.type) {
        this.addComponent(event.detail);
      }
    },
    /**
     * Adds a registered section, unless its isEnabled, given the params,
     * answers false. Its init option is not read.
     *
     * @param {Object} component the registered component
     * @returns {Promise<void>} settled once the section is added or skipped
     */
    async addComponent(component) {
      const isEnabled = component.componentOptions.isEnabled;
      if (isEnabled && !(await Promise.resolve(isEnabled(this.params)))) {
        return;
      }
      const components = this.components.filter(existing => existing.componentOptions.id !== component.componentOptions.id);
      components.push(component);
      components.sort((first, second) => (first.componentOptions.rank || 0) - (second.componentOptions.rank || 0));
      this.components = components;
    },
    sections() {
      return this.$refs.sections || [];
    },
    /**
     * Tells whether one section holds a change: its hasChanges(), or a
     * non-empty fields() for a section that has no hasChanges().
     *
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
     * Watches each new section's change state. The getter reads the
     * section's reactive data through its methods, so the watcher fires on
     * the section's own input.
     *
     * @returns {void}
     */
    watchSections() {
      this.sections().filter(section => !this.watched.has(section)).forEach(section => {
        this.watched.add(section);
        this.$watch(() => this.sectionChanged(section), () => this.$emit('changed', this.hasChanges()));
      });
    },
    call(method, ...args) {
      return this.sections()
        .filter(section => typeof section[method] === 'function')
        .map(section => section[method](...args));
    },
    validate() {
      return Promise.all(this.call('validate')).then(results => results.every(Boolean));
    },
    values() {
      return Object.assign({}, ...this.call('values'));
    },
    fields() {
      return Object.assign({}, ...this.call('fields'));
    },
    hasChanges() {
      return this.sections().some(this.sectionChanged);
    },
    saved() {
      this.call('saved');
    },
    markRefused(fieldKey, code) {
      this.call('markRefused', fieldKey, code);
    },
    reset() {
      this.call('reset');
    },
  },
};
</script>
