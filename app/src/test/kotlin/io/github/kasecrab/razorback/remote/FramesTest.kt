package io.github.kasecrab.razorback.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * The wire format, against the shapes the harness actually writes.
 *
 * Every string below is one `crates/ah-remote-proto` produces. The two are
 * separate implementations of one format, so what is worth testing is that
 * this one reads what that one writes.
 */
class FramesTest {

    @Test
    fun anEventFrameIsReadAsOne() {
        val frame = Frames.readEnvelope(
            """{"t":"evt","v":1,"link":"ab","seq":7,"ct":"c2VhbGVk","n":42}""",
        )
        assertTrue(frame is Frames.Envelope.Evt)
        frame as Frames.Envelope.Evt
        assertEquals("ab", frame.link)
        assertEquals(7L, frame.seq)
        assertEquals(42L, frame.n)
    }

    @Test
    fun aFrameFromAnotherProtocolIsRefused() {
        assertNull(Frames.readEnvelope("""{"t":"evt","v":99,"link":"ab","seq":1,"ct":"x","n":1}"""))
        assertNull(Frames.readEnvelope("not json at all"))
    }

    @Test
    fun somethingThisBuildHasNoIdeaAboutIsNotGuessedAt() {
        assertEquals(Frames.Envelope.Unknown, Frames.readEnvelope("""{"t":"dancing","v":1}"""))
    }

    @Test
    fun aSkewComplaintCarriesTheRelaysClock() {
        val frame = Frames.readEnvelope("""{"t":"ctl","v":1,"e":"skew","server_ms":1700}""")
        frame as Frames.Envelope.Ctl
        assertEquals("skew", frame.error)
        assertEquals(1700L, frame.serverMs)
    }

    @Test
    fun aMachineSayingHelloIsReadAsOne() {
        val payload = Frames.readPayload(
            """{"k":"hello","host":"rabe","os":"linux","ah_version":"0.1.0","proto":1,"holder":"tui"}"""
                .toByteArray(),
        )
        payload as Frames.FromDesk.Hello
        assertEquals("rabe", payload.machine.host)
        assertEquals("tui", payload.machine.holder)
    }

    @Test
    fun aSessionListIsReadWithTheLiveOneMarked() {
        val payload = Frames.readPayload(
            """{"k":"sessions","list":[
                {"id":"abc","title":"fix the test","cwd":"/home/x","model":"m",
                 "started_ms":1,"messages":4,"live":true},
                {"id":"def","name":"old","title":"t","cwd":"/home/y","model":"m",
                 "started_ms":2,"messages":1,"live":false}]}""".toByteArray(),
        )
        payload as Frames.FromDesk.Sessions
        assertEquals(2, payload.list.size)
        assertTrue(payload.list[0].live)
        assertNull(payload.list[0].name)
        assertEquals("old", payload.list[1].name)
    }

    @Test
    fun aToolQuestionSaysWhatItIsAsking() {
        val payload = Frames.readPayload(
            """{"k":"ask_permission","session":"abc","id":42,
                "call":{"id":"c1","type":"function","function":{"name":"bash","arguments":"{}"}},
                "reason":"it writes"}""".toByteArray(),
        )
        payload as Frames.FromDesk.Ask
        assertTrue(payload.isTool)
        assertEquals("bash", payload.question.what)
        assertEquals(42L, payload.question.id)
    }

    @Test
    fun aRefusalCarriesItsReasonAndAnAcceptanceDoesNot() {
        val no = Frames.readPayload(
            """{"k":"ack","cmd_seq":1,"ok":false,"error":"that question has been answered"}"""
                .toByteArray(),
        ) as Frames.FromDesk.Ack
        assertEquals("that question has been answered", no.error)
        val yes = Frames.readPayload("""{"k":"ack","cmd_seq":1,"ok":true}""".toByteArray())
            as Frames.FromDesk.Ack
        assertTrue(yes.ok)
        assertNull(yes.error)
    }

    @Test
    fun whatThisPhoneSaysIsShapedTheWayTheHarnessReadsIt() {
        val submit = JSONObject(String(Frames.submit("abc", "carry on")))
        assertEquals("submit", submit.getString("k"))
        assertEquals("abc", submit.getString("session"))
        assertEquals("carry on", submit.getString("text"))

        val answer = JSONObject(String(Frames.answerTool("abc", 42, true)))
        assertEquals("answer_permission", answer.getString("k"))
        assertTrue(answer.getBoolean("allow"))

        val ask = JSONObject(String(Frames.answerAsk("abc", 7, "Yes", "but carefully")))
        assertEquals("answer_ask", ask.getString("k"))
        assertEquals("answered", ask.getJSONObject("reply").getString("reply"))
        val first = ask.getJSONObject("reply").getJSONArray("answers").getJSONObject(0)
        assertEquals("Yes", first.getJSONArray("picked").getString(0))
        assertEquals("but carefully", first.getString("note"))
        val dismissed = JSONObject(String(Frames.dismissAsk("abc", 7)))
        assertEquals("dismissed", dismissed.getJSONObject("reply").getString("reply"))
        assertEquals("detach", JSONObject(String(Frames.detach("abc"))).getString("k"))

        val sub = JSONObject(Frames.subscribe(9, 200))
        assertEquals("sub", sub.getString("t"))
        assertEquals(1, sub.getInt("v"))
        assertEquals(9, sub.getInt("since"))
    }

    @Test
    fun aQuestionCarriesItsChoicesAndHowManyQuestionsThereAre() {
        val payload = Frames.readPayload(
            """{"k":"ask_user","session":"abc","id":3,"ask":{"questions":[
            {"header":"Branch","question":"Which branch?","options":[{"label":"main"},{"label":"dev","description":"the risky one"}]},
            {"question":"And why?"}]}}""".toByteArray(),
        ) as Frames.FromDesk.Ask
        assertEquals(false, payload.isTool)
        assertEquals("Which branch?", payload.question.what)
        assertEquals("Branch", payload.question.header)
        assertEquals(listOf("main", "dev"), payload.question.options)
        assertEquals(2, payload.question.count)
    }

    @Test
    fun theTextOfAnEventIsTheTextAndTheRestIsSkipped() {
        assertEquals("hello", Frames.eventText(JSONObject("""{"type":"text","text":"hello"}""")))
        assertNull(Frames.eventText(JSONObject("""{"type":"usage","usage":{}}""")))
        assertEquals(
            "bash",
            Frames.eventTool(
                JSONObject("""{"type":"tool_start","call":{"function":{"name":"bash"}}}"""),
            ),
        )
    }
}
