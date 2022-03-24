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