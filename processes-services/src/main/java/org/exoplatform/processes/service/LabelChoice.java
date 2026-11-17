/*
 * Copyright (C) 2026 eXo Platform SAS
 *
 *  This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <gnu.org/licenses>.
 */
package org.exoplatform.processes.service;

import org.exoplatform.task.dto.LabelDto;

/**
 * A label a process save keeps: a label of the process's project, or a name
 * to create there. It is a step of the Service's save only, never exposed,
 * so it stays package-private beside the Service rather than a public model.
 *
 * @param existing the project's label, or null
 * @param name its name
 */
record LabelChoice(LabelDto existing, String name) {
}
