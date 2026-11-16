package com.wangzan101.mika.core.cap.career

import com.wangzan101.mika.core.tool.AgentTool

/**
 * The full career tool surface. These are registered through [CareerCapability] instead of
 * [com.wangzan101.mika.core.assembly.allTools] so the general-purpose prompt and its golden tests
 * stay stable; they are still declared to [com.wangzan101.mika.core.assembly.knownToolNames] so
 * skill and documentation references validate.
 *
 * Deliberately absent: a submit tool or an account-creation tool. Both are user actions in the
 * Career screen; a model may prepare and request approval, never perform them.
 */
internal object CareerTools {
    val all: List<AgentTool> = listOf(
        CareerProfileTool,
        CareerResumeTool,
        CareerCoverLetterTool,
        CareerDocumentTool,
        JobSearchTool,
        JobExtractTool,
        JobMatchTool,
        JobApplicationTool,
        CareerInterviewTool,
        CareerDashboardTool,
    )

    val names: Set<String> = all.map { it.name }.toSet()
}
