package org.exoplatform.processes.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkFilter {

    private String status;
    private String query;
    private Boolean isDraft;
    private Boolean completed;

    /**
     * Whether each listed request carries its comments count, replies
     * included, read from its task; null or false leaves the count unset.
     */
    private Boolean withCommentsCount;
}
