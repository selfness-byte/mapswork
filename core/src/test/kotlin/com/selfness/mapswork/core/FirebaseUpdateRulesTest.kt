package com.selfness.mapswork.core

import kotlin.test.*
import org.junit.Test

class FirebaseUpdateRulesTest {
    @Test fun projectSiteProducesTheExactHttpsManifestAddress() {
        assertEquals("https://inanimals-1977-mapswork.web.app/updates/update.json",
            UpdateRules.manifestUrl("inanimals-1977-mapswork"))
    }
    @Test fun anUnconfiguredSiteRetainsTheExistingGitHubEndpoint() {
        assertEquals(UpdateRules.MANIFEST_URL, UpdateRules.manifestUrl(""))
    }
    @Test fun whitespaceAroundTheSiteIsRemoved() {
        assertEquals(UpdateRules.manifestUrl("inanimals-1977-mapswork"),
            UpdateRules.manifestUrl("  inanimals-1977-mapswork  "))
    }
    @Test fun aSiteCannotInjectAnotherHostPathOrCredential() {
        listOf("https://other.web.app", "other.web.app", "user@site", "site/path", "site?token=x",
            "../site", "-site", "site-", "SITE", "a".repeat(64)).forEach {
            assertFailsWith<IllegalArgumentException>(it) { UpdateRules.manifestUrl(it) }
        }
    }
    @Test fun automaticChecksWaitAFullDay() {
        val previous = 1_000_000L
        assertFalse(AutomaticUpdateRules.isDue(previous + 86_399_999, previous))
        assertTrue(AutomaticUpdateRules.isDue(previous + 86_400_000, previous))
    }
    @Test fun firstLaunchAndClockRollbackCanCheck() {
        assertTrue(AutomaticUpdateRules.isDue(1_000_000, 0))
        assertTrue(AutomaticUpdateRules.isDue(900_000, 1_000_000))
    }
}
