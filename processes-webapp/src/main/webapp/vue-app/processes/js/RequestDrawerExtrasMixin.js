/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * The request drawer's fill state, its « Save as draft » action and its full
 * screen state. The fill state is what the add-on sections report through
 * their fillState(): the fields shown, those holding a value, and the
 * required ones holding none; it stays empty when no section reports one.
 * Saving as draft goes through the draft mixin's save, so a draft is still
 * created only once the user wrote something. The drawer provides
 * RequestDraftMixin and calls initExtrasState() from each opening.
 */
export default {
  data: () => ({
    sectionsFill: {total: 0, filled: 0, missing: []},
    expanded: false,
    savingAsDraft: false,
  }),
  computed: {
    /**
     * True once there is a draft whose description the draft mixin may send,
     * or an input that creates one: the draft mixin's own creation rule, so
     * an empty request never gets a draft.
     *
     * @returns {Boolean} true when « Save as draft » may run
     */
    canSaveAsDraft() {
      if (this.saving || this.savingAsDraft) {
        return false;
      }
      return this.hasDraft ? this.emptyOrValidWorkDescription : this.descriptionHasText || this.sectionsChanged;
    },
  },
  methods: {
    /**
     * Starts the fill and full screen state of a newly opened drawer: its
     * content stays mounted across openings, so the last opening's state is
     * forgotten here, before the sections report their own.
     *
     * @returns {void}
     */
    initExtrasState() {
      this.sectionsFill = {total: 0, filled: 0, missing: []};
      this.expanded = false;
      this.savingAsDraft = false;
    },
    /**
     * Records the fill state the sections reported.
     *
     * @param {Object} fill {total, filled, missing}
     * @returns {void}
     */
    onSectionsFill(fill) {
      this.sectionsFill = fill || {total: 0, filled: 0, missing: []};
      // The draft mixin lets a draft holding form values alone be sent
      this.sectionsFilled = this.sectionsFill.filled > 0;
    },
    /**
     * Saves the draft as it stands and closes the drawer once it is saved;
     * a refused save keeps the drawer open, its refused field marked by the
     * draft mixin. An answer that comes after the drawer was opened again
     * acts on nothing.
     *
     * @returns {Promise} settled once the drawer is closed or kept
     */
    saveAsDraft() {
      if (!this.canSaveAsDraft) {
        return Promise.resolve();
      }
      const openKey = this.openKey;
      this.savingAsDraft = true;
      return this.saveDraft()
        .then(() => {
          if (openKey !== this.openKey || !this.hasDraft) {
            return;
          }
          this.$root.$emit('show-alert', {type: 'success', message: this.$t('processes.request.draftSaved')});
          if (this.$refs.work && this.$refs.work.drawer) {
            // A drawer the user closed meanwhile has already finished its close
            this.close();
          }
        })
        .catch(() => null)
        .finally(() => {
          if (openKey === this.openKey) {
            this.savingAsDraft = false;
          }
        });
    },
  },
};
