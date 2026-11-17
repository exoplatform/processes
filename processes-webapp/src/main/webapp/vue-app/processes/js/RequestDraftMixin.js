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
 * The request drawer's draft: created by the user's first input (a
 * description character, a change in an add-on's section, opening the
 * attachments),
 * never by the drawer's opening, then saved on the description's blur and on
 * close. Saves run one at a time: each one waits for the previous, reads what
 * to send when it runs, and sends nothing when nothing changed, so two queued
 * saves send once and a refused one never blocks the next. The add-on
 * sections (ref requestSections) send their values with each save and are
 * told when a save succeeded or which field a refusal names. The drawer
 * provides work, editDraft, viewMode, saving, emptyOrValidWorkDescription,
 * entityType, attachments and openKey, which each opening increments. A
 * refused save rejects with an error carrying the refusal: its code and the
 * refused field's key, the code being the generic message for a refusal the
 * user cannot act on (403, 404, 500).
 */
export default {
  data: () => ({
    draftChain: Promise.resolve(),
    pendingDraftSave: null,
    savedDescription: '',
    sectionsChanged: false,
    submitted: false,
  }),
  computed: {
    hasDraft() {
      return !!(this.editDraft && this.work && this.work.id);
    },
    descriptionHasText() {
      return !!this.$utils.htmlToText(this.work && this.work.description || '').trim();
    },
    hasChanges() {
      return (this.work && this.work.description || '') !== this.savedDescription || this.sectionsChanged;
    },
  },
  watch: {
    descriptionHasText(value) {
      if (value && !this.hasDraft) {
        this.saveDraft().catch(() => null);
      }
    },
  },
  created() {
    this.$root.$on('pre-save-work-draft-added', this.settleDraftSave);
    this.$root.$on('work-draft-updated', () => this.settleDraftSave(null));
    this.$root.$on('work-draft-save-failed', this.refuseDraftSave);
    this.$root.$on('work-add-failed', this.refuseSubmit);
  },
  methods: {
    requestSections() {
      return this.$refs.requestSections || null;
    },
    /**
     * Starts the draft state of a newly opened drawer, with the description
     * the drawer opened with as the last saved one.
     *
     * @returns {void}
     */
    initDraftState() {
      this.savedDescription = this.work && this.work.description || '';
      this.sectionsChanged = false;
      this.submitted = false;
    },
    /**
     * Records whether a section holds a change; the first one creates the
     * draft.
     *
     * @param {Boolean} changed true when a section holds a change
     * @returns {void}
     */
    onSectionsChanged(changed) {
      this.sectionsChanged = changed;
      if (changed && !this.hasDraft) {
        this.saveDraft().catch(() => null);
      }
    },
    /**
     * Queues a draft save after the one in flight.
     *
     * @param {Boolean} create true to create the draft even with no input
     * @returns {Promise} settled with this save, rejected when it is refused
     */
    saveDraft(create) {
      const save = this.draftChain.catch(() => null).then(() => this.sendDraft(create));
      this.draftChain = save;
      return save;
    },
    /**
     * Sends the draft as it stands: a creation when there is none yet and
     * there is input, or create is set; an update when something changed.
     *
     * @param {Boolean} create true to create the draft even with no input
     * @returns {Promise} settled once the server answered
     */
    sendDraft(create) {
      if (this.viewMode || this.submitted || this.saving) {
        return Promise.resolve();
      }
      const creating = !this.hasDraft;
      if (creating ? !(create || this.descriptionHasText || this.sectionsChanged) : !this.hasChanges) {
        return Promise.resolve();
      }
      if (!creating && !this.emptyOrValidWorkDescription) {
        // A description over its limit refuses the update, so a close waiting for it keeps the drawer. The form's
        // validity is not asked: a section's field marked refused is invalid, and a draft keeps it
        return Promise.reject(this.refusalError({code: 'processes.request.draftInvalid', fieldKey: null}));
      }
      const draft = this.draftBody(creating);
      const openKey = this.openKey;
      const answer = new Promise((resolve, reject) => this.pendingDraftSave = {resolve, reject});
      this.$root.$emit(creating ? 'create-work-draft' : 'update-work-draft', creating ? {draft} : draft);
      return answer.then(created => {
        if (openKey !== this.openKey) {
          // The drawer was opened on another request meanwhile: this answer is not its own
          return;
        }
        if (created) {
          this.bindCreatedDraft(created);
        }
        this.savedDescription = draft.description;
        if (this.requestSections()) {
          this.requestSections().saved();
        }
      });
    },
    draftBody(creating) {
      const values = this.requestSections() && this.requestSections().values() || {};
      return {
        id: creating ? 0 : this.work.id,
        title: this.work.title || '',
        createdTime: this.work.createdTime,
        creatorId: this.work.creatorId,
        description: this.work.description || '',
        workFlow: this.work.workFlow,
        isDraft: true,
        formValues: Object.keys(values).length ? JSON.stringify(values) : null,
      };
    },
    /**
     * Binds the drawer to the draft its first input created, keeping what was
     * typed meanwhile, and lists the attachments the draft got from its
     * process.
     *
     * @param {Object} draft the created draft
     * @returns {void}
     */
    bindCreatedDraft(draft) {
      this.$set(this.work, 'id', draft.id);
      this.editDraft = true;
      if (this.$refs.work && this.$refs.work.drawer) {
        // A drawer already closed keeps the address its close set
        this.$root.$emit('update-url-path', 'draftDetails', `/myRequests/draftDetails/${draft.id}`);
      }
      this.$root.$emit('init-list-attachments', {entityId: draft.id, entityType: this.entityType});
    },
    settleDraftSave(draft) {
      if (this.pendingDraftSave) {
        this.pendingDraftSave.resolve(draft);
        this.pendingDraftSave = null;
      }
    },
    refuseDraftSave(refusal) {
      this.markRefused(refusal);
      if (this.pendingDraftSave) {
        this.pendingDraftSave.reject(this.refusalError(refusal));
        this.pendingDraftSave = null;
      }
    },
    /**
     * Keeps the drawer as it was before a refused submit: its draft is named
     * again by its id, and the refused field is marked.
     *
     * @param {Object} refusal the refusal's code and field key
     * @returns {void}
     */
    refuseSubmit(refusal) {
      this.saving = false;
      if (this.work.draftId) {
        this.work.id = this.work.draftId;
        this.work.draftId = null;
      }
      this.markRefused(refusal);
    },
    refusalError(refusal) {
      return Object.assign(new Error(refusal && refusal.code || 'refused'), {code: null, fieldKey: null}, refusal);
    },
    markRefused(refusal) {
      if (refusal && refusal.fieldKey && this.requestSections()) {
        this.requestSections().markRefused(refusal.fieldKey, refusal.code);
      }
    },
    /**
     * Sends the request once the sections' check passed and the draft save
     * in flight, if any, is answered; the draft is named by its id.
     *
     * @returns {Promise} settled once the request is sent or the check failed
     */
    submitWork() {
      this.saving = true;
      const sections = this.requestSections();
      return this.draftChain.catch(() => null)
        .then(() => (sections ? sections.validate() : true))
        .then(valid => {
          if (!valid) {
            this.saving = false;
            return;
          }
          const values = sections && sections.values() || {};
          if (this.editDraft) {
            this.work.draftId = this.work.id;
            this.work.id = 0;
          }
          this.work.attachments = this.attachments;
          this.work.formValues = Object.keys(values).length ? JSON.stringify(values) : null;
          this.$root.$emit('add-work', this.work);
        });
    },
    /**
     * Forgets what was typed once the request is sent, so no draft save
     * follows the close.
     *
     * @returns {void}
     */
    resetAfterSubmit() {
      this.submitted = true;
      this.saving = false;
      if (this.requestSections()) {
        this.requestSections().reset();
      }
    },
  },
};
