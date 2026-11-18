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
 * Gives the request drawer's message editor (ck-editor-type processesRequest)
 * its own « attach a file » button, which dispatches open-request-attachments
 * for the drawer to answer. Registered once, when the drawer's code loads,
 * before the editor reads the registry.
 */
extensionRegistry.registerExtension('RichEditor', 'ckeditor-extensions-processesRequest', {
  id: 'attachRequestFile',
  rank: 30,
  enabled: () => true,
  getExtension: () => {
    if (window.CKEDITOR) {
      CKEDITOR.plugins.addExternal('attachRequestFile', '/processes/ckeditor/attachRequestFile/', 'plugin.js');
    }
    return {
      extraPlugin: 'attachRequestFile',
      extraToolbarItem: 'attachRequestFile',
    };
  },
});
