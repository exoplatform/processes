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
    /**
     * Filters on the completion of the request's task: true for the requests
     * closed (decided or cancelled), false for the pending ones, null for both.
     */
    private Boolean completed;

    /**
     * Filters on the requester's archive (the request record's flag, not the
     * task): true for the archived requests, false for the others, null for
     * both. « My requests » lists by this flag.
     */
    private Boolean archived;

    /**
     * Whether each listed request carries its comments count, replies
     * included, read from its task; null or false leaves the count unset.
     */
    private Boolean withCommentsCount;
}
