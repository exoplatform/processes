/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
/**
 * The processes filed under the platform's categories: the page's category tree, the grouping of
 * the processes by category and the categories of one process. Pure functions; the components hold
 * the state.
 */

/** The object type of a process among the platform's categories. */
export const CATEGORY_OBJECT_TYPE = 'process';

/** The depth the page's category tree is read to: the server's filter reaches as deep. */
export const CATEGORY_DEPTH = 4;

/**
 * Reads the categories the user may filter the processes by: those of the processes they see.
 *
 * @param {object} categoryService social's category service, absent when social ships none
 * @returns {Promise<Map<number, object>>} every category below the root, by id; empty when none
 *          could be read
 */
export function loadCategories(categoryService) {
  if (!categoryService) {
    return Promise.resolve(new Map());
  }
  return categoryService.getCategoryTree({
    objectType: CATEGORY_OBJECT_TYPE,
    depth: CATEGORY_DEPTH,
    offset: 0,
    limit: -1,
  })
    .then(flattenCategories)
    .catch(() => new Map());
}

/**
 * @param {object} tree the category tree read for processes, its root included
 * @returns {Map<number, object>} every category below the root, by id: its name, its parent's id
 *          and its path of names
 */
export function flattenCategories(tree) {
  const categories = new Map();
  const visit = (category, parentId, path) => {
    (category?.categories || []).forEach(child => {
      const childPath = [...path, child.name];
      categories.set(child.id, {id: child.id, name: child.name, parentId, path: childPath});
      visit(child, child.id, childPath);
    });
  };
  visit(tree, null, []);
  return categories;
}

/**
 * Groups the shown processes by category: a group per category a process is filed under, by
 * name, a process filed under two showing in both, then the processes filed under none.
 *
 * @param {Array<object>} workflows the processes shown, in the page's order
 * @param {Map<number, object>} categories the page's categories
 * @returns {Array<object>} the groups, each with its key, its category (null for the processes
 *          filed under none) and its processes
 */
export function groupByCategory(workflows, categories) {
  const groups = new Map();
  const none = [];
  workflows.forEach(workflow => {
    const known = (workflow.categoryIds || []).filter(id => categories.has(id));
    if (!known.length) {
      none.push(workflow);
    }
    known.forEach(id => {
      if (!groups.has(id)) {
        groups.set(id, {key: `category-${id}`, category: categories.get(id), workflows: []});
      }
      groups.get(id).workflows.push(workflow);
    });
  });
  const sorted = [...groups.values()].sort((a, b) => a.category.name.localeCompare(b.category.name));
  if (none.length) {
    sorted.push({key: 'category-none', category: null, workflows: none});
  }
  return sorted;
}

/**
 * @param {object} workflow a process
 * @param {Map<number, object>} categories the page's categories
 * @returns {Array<object>} the categories it is filed under that the page knows, by name
 */
export function categoriesOf(workflow, categories) {
  return (workflow.categoryIds || [])
    .filter(id => categories.has(id))
    .map(id => categories.get(id))
    .sort((a, b) => a.name.localeCompare(b.name));
}
