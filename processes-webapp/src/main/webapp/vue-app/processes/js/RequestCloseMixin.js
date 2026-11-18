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
 * Closing the request drawer: the draft is saved first, and a close whose
 * save was refused either opens the drawer again or ends the close. The
 * drawer provides RequestDraftMixin, its messageRow ref and its work ref.
 */
export default {
  data: () => ({
    // Codes refusing what the drawer sent for a field, not a value the user typed
    unfixableFieldCodes: [
      'forms.value.unknownField',
      'forms.value.unknownReferenceKind',
      'forms.value.nullNotAllowed',
      'forms.value.numberAsString',
      'forms.value.invalidType',
    ],
  }),
  methods: {
    /**
     * Closes the drawer after saving the draft. A drawer still open is closed
     * first, and its closed event comes back here. When a section holds
     * changes, the close waits for the save, and a refused one is answered by
     * afterRefusedClose, which opens the drawer again only on a refusal the
     * user can act on.
     *
     * @returns {void}
     */
    close() {
      if (this.$refs.work.drawer) {
        this.$refs.work.close();
        return;
      }
      const openKey = this.openKey;
      if (this.saving && this.pendingSubmit) {
        // A sent request closes the drawer itself on work-added; a refused one is answered as a refused save
        this.pendingSubmit.catch(refusal => openKey === this.openKey && this.afterRefusedClose(refusal));
        return;
      }
      const waits = !this.viewMode && !this.submitted && this.sectionsChanged;
      const save = this.viewMode ? Promise.resolve() : this.saveDraft();
      if (waits) {
        save.then(() => openKey === this.openKey && this.finishClose())
          .catch(refusal => openKey === this.openKey && this.afterRefusedClose(refusal));
      } else {
        save.catch(() => null);
        this.finishClose();
      }
    },
    /**
     * Answers a close whose save was refused: a refusal the user can act on
     * opens the drawer again with what was typed, any other closes it, since
     * neither typing nor sending again changes it. The user can act on a
     * refusal that names a field whose value they can change, a message over
     * its length limit, a submit the sections' own check stopped, and a
     * concurrent update, which the next save sends again. A field refused for the shape the drawer sent, rather than for
     * its value, closes the drawer.
     *
     * @param {Object} refusal the refusal's code and field key
     * @returns {void}
     */
    afterRefusedClose(refusal) {
      const code = refusal && refusal.code || '';
      const fieldFixable = refusal && refusal.fieldKey && !this.unfixableFieldCodes.includes(code);
      if (!fieldFixable && !['processes.request.draftInvalid', 'processes.request.sectionsInvalid', 'forms.value.concurrentUpdate'].includes(code)) {
        this.finishClose();
        return;
      }
      if (code === 'processes.request.draftInvalid') {
        window.setTimeout(() => this.$refs?.messageRow?.setFocus(true), 200);
        this.$root.$emit('show-alert', {type: 'error', message: this.$t('processes.work.form.description.maxLength.message', {0: this.maxLength})});
      }
      const draftPath = `/myRequests/draftDetails/${this.work.id}`;
      if (this.hasDraft && !document.location.pathname.includes(draftPath)) {
        // The draft was created while the drawer was closed, which left the address unchanged
        this.$root.$emit('update-url-path', 'draftDetails', draftPath);
      }
      this.$refs.work.open();
    },
    /**
     * Ends a close: the attachments are reset and the address names the list the drawer was opened from.
     * @returns {void}
     */
    finishClose() {
      this.$root.$emit('add-work-drawer-closed');
      this.$root.$emit('reset-list-attachments');
      if (document.location.pathname.includes('myRequests')) {
        this.$root.$emit('update-url-path', 'myRequest', '/myRequests');
      } else {
        this.$root.$emit('update-url-path', 'processes', '');
      }
    }
  }
};
