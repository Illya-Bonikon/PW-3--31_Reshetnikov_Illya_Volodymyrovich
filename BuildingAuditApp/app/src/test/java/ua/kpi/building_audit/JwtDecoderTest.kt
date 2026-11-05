package ua.kpi.building_audit

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kpi.building_audit.auth.InvalidTokenException
import ua.kpi.building_audit.auth.JwtDecoder
import ua.kpi.building_audit.auth.Permission
import ua.kpi.building_audit.auth.Role
import java.util.Base64

class JwtDecoderTest {
    private val decoder = JwtDecoder(Gson())

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "${encoder.encodeToString("{}".toByteArray())}.${encoder.encodeToString(payload.toByteArray())}.signature"
    }

    @Test
    fun decodesClaimsWithCyrillicName() {
        val session = decoder.decode(token("""{"sub":"auditor","name":"Коваленко Олена","role":"AUDITOR","iat":100,"exp":700}"""))
        assertEquals("Коваленко Олена", session.fullName)
        assertEquals(Role.AUDITOR, session.role)
        assertEquals(600L, session.secondsLeft(100_000))
        assertTrue(session.isExpired(700_000))
    }

    @Test
    fun rolePermissionsDiffer() {
        assertTrue(Role.ADMIN.permissions.containsAll(Permission.entries))
        assertTrue(Permission.EDIT_BUILDINGS in Role.AUDITOR.permissions)
        assertFalse(Permission.DELETE_BUILDINGS in Role.AUDITOR.permissions)
        assertEquals(setOf(Permission.VIEW_BUILDINGS), Role.VIEWER.permissions)
    }

    @Test(expected = InvalidTokenException::class)
    fun rejectsUnknownRole() {
        decoder.decode(token("""{"sub":"x","role":"ROOT","exp":1}"""))
    }

    @Test(expected = InvalidTokenException::class)
    fun rejectsWrongStructure() {
        decoder.decode("only.two")
    }
}
