package com.arulm.loaner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test fun detectsNewPatchVersion() = assertTrue(VersionComparator.isNewer("2.1", "2.0"))
    @Test fun detectsNewMajorVersionNumerically() = assertTrue(VersionComparator.isNewer("10.0", "2.1"))
    @Test fun equalVersionIsNotNewer() = assertFalse(VersionComparator.isNewer("2.1.0", "2.1"))
    @Test fun olderVersionIsNotNewer() = assertFalse(VersionComparator.isNewer("1.9", "2.1"))
}
