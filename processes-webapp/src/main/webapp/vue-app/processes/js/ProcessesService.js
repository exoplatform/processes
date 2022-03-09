export function getWorkFlows(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value != null) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });

}
export function getWorks(itemsFilter, offset, limit, expand) {
  const formData = new FormData();
  if (itemsFilter) {
    Object.keys(itemsFilter).forEach(key => {
      const value = itemsFilter[key];
      if (value) {
        formData.append(key, value);
      }
    });
  }
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function addNewWorkFlow(workflow) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workflow, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while adding a new workflow');
    } else {
      return resp.json();
    }
  });
}

export function addWork(work) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/works`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(work, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while adding a new work');
    } else {
      return resp.json();
    }
  });
}

export function isProcessesManager() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/permissions`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while checking user permissions');
    } else {
      return resp.text();
    }
  });
}

export function deleteWorkflowById(workflowId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflow/${workflowId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a workflow');
    } else {
      return resp.text();
    }
  });
}

export function updateWorkflow(workflow) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workflows`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workflow, (key, value) => {
      if (value !== null) {
        return value;
      }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while updating a workflow');
    } else {
      return resp.text();
    }
  });
}

export function getProcessesSpaceInfo() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/processesSpace/info`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting processes space info');
    } else {
      return resp.json();
    }
  });
}

export function countWorksByWorkflow(workflowId, isCompleted) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/countWorks/${workflowId}?isCompleted=${isCompleted}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting works count');
    } else {
      return resp.text();
    }
  });
}

export function getWorkComments(workId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/tasks/comments/${workId}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error when getting task comments');
    }
  });
}

export function deleteWorkById(workId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/work/${workId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a work');
    } else {
      return resp.text();
    }
  });
}

export function getWorkDrafts(offset, limit, expand) {
  const formData = new FormData();
  if (expand) {
    formData.append('expand', expand);
  }
  if (offset) {
    formData.append('offset', offset);
  }
  if (limit) {
    formData.append('limit', limit);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDrafts?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while getting work drafts');
    } else {
      return resp.json();
    }
  });
}

export function createWorkDraft(workDraft) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workDraft, (key, value) => {
      if (value !== null) { return value; }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while creating a new work draft');
    } else {
      return resp.json();
    }
  });
}

export function updateWorkDraft(workDraft) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(workDraft, (key, value) => {
      if (value !== null) {
        return value;
      }
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while updating a work draft');
    } else {
      return resp.text();
    }
  });
}

export function deleteWorkDraftById(draftId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/processes/workDraft/${draftId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error while deleting a work draft');
    } else {
      return resp.text();
    }
  });
}