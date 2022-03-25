/*
 * Copyright (C) 2022 eXo Platform SAS.
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
export function createNewFormDoc(title, templateName, pathDrive, path, entityType, entityId) {
  const formData = new FormData();

  if (title) {
    formData.append('title', title);
  }
  if (templateName) {
    formData.append('templateName', templateName);
  }
  if (pathDrive) {
    formData.append('pathDrive', pathDrive);
  }
  if (path) {
    formData.append('path', path);
  }
  if (entityType) {
    formData.append('entityType', entityType);
  }
  if (entityId) {
    formData.append('entityId', entityId);
  }

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/attachment/newDoc`, {
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      if (resp.status === 409) {
        return resp;
      }
      throw new Error('Error creating new document');
    } else {
      return resp.json();
    }
  });
}

export function getEntityAttachments(entityType, entityId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/attachments/${entityType}/${entityId}`, {
    credentials: 'include',
    method: 'GET',
  }).then((resp) => {
    if (resp || resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting entity\'s linked attachments');
    }
  });
}