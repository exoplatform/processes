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

/*
 * The request drawer's « attach a file » button. It mirrors the platform's
 * attachFile plugin but dispatches its own event, open-request-attachments,
 * so it never opens the activity or notes composer's attachments.
 */
CKEDITOR.plugins.add('attachRequestFile', {
  icons: 'attachRequestFile',
  lang: ['en'],
  init: function(editor) {
    editor.addCommand('attachRequestFile', {
      exec: function() {
        document.dispatchEvent(new CustomEvent('open-request-attachments'));
      }
    });
    editor.ui.addButton('attachRequestFile', {
      label: editor.lang.attachRequestFile.buttonTooltip,
      command: 'attachRequestFile',
      toolbar: 'insert'
    });
    // Keep the icon within the standard toolbar icon box, as its siblings
    editor.on('instanceReady', function() {
      try {
        const container = editor.container && editor.container.$;
        const icon = container && container.querySelector('.cke_button__attachrequestfile_icon');
        if (icon) {
          icon.style.setProperty('background-size', 'contain', 'important');
          icon.style.setProperty('background-repeat', 'no-repeat', 'important');
          icon.style.setProperty('background-position', 'center', 'important');
        }
      } catch (e) {
        // Non-fatal: the button still works, only its icon sizing is skipped
      }
    });
  }
});
