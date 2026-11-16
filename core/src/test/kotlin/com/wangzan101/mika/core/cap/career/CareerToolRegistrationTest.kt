package com.wangzan101.mika.core.cap.career

import com.wangzan101.mika.core.assembly.knownToolNames
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerToolRegistrationTest {
    @Test fun everyCareerToolIsDeclaredInTheKnownNames() {
        assertTrue(CareerTools.names.isNotEmpty())
        assertTrue(knownToolNames().containsAll(CareerTools.names))
    }

    @Test fun noModelCallableSubmissionOrEraseToolExists() {
        assertFalse(CareerTools.names.any { "submit" in it || "erase" in it || "delete" in it })
        val profile = CareerProfileTool.def
        assertFalse("delete" in profile.props.keys)
        assertFalse("confirm" in profile.props.keys)
    }

    @Test fun everyToolHasAUniqueNameAndANonBlankDescription() {
        assertTrue(CareerTools.all.map { it.name }.distinct().size == CareerTools.all.size)
        assertTrue(CareerTools.all.all { it.def.description.isNotBlank() })
        assertTrue(CareerTools.all.all { it.def.required.isNotEmpty() || it.def.props.isEmpty() })
    }
}
