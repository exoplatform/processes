export function getWorkFlows(itemsFilter, offset, limit, expand) {
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

