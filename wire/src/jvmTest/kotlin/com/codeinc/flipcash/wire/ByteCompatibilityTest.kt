package com.codeinc.flipcash.wire

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.messaging.v1.Model
import com.codeinc.flipcash.wire.common.v1.UserId
import com.codeinc.flipcash.wire.messaging.v1.Content
import com.codeinc.flipcash.wire.messaging.v1.Emoji
import com.codeinc.flipcash.wire.messaging.v1.EmojiReaction
import com.codeinc.flipcash.wire.messaging.v1.LinkPreview
import com.codeinc.flipcash.wire.messaging.v1.Message
import com.codeinc.flipcash.wire.messaging.v1.MessageId
import com.codeinc.flipcash.wire.messaging.v1.ReactionSummary
import com.codeinc.flipcash.wire.messaging.v1.Reactor
import com.codeinc.flipcash.wire.messaging.v1.ReplyContent
import com.codeinc.flipcash.wire.messaging.v1.TextContent
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ofEpochSecond
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The same message built with protobuf-java and with Wire must encode to the same bytes, and
 * each library must decode the other's output back to an equal message. Every scalar, repeated
 * and nested field of messaging.v1.Message is set, and two arms of the Content oneof are
 * covered, so a default instance cannot pass by encoding to nothing.
 */
class ByteCompatibilityTest {
    private val sender = ByteArray(32) { (it + 1).toByte() }
    private val reactor = ByteArray(32) { (0xA0 + it).toByte() }
    private val seconds = 1_762_790_400L
    private val nanos = 123_456_789

    private fun javaTimestamp(offset: Long = 0) =
        Timestamp.newBuilder().setSeconds(seconds + offset).setNanos(nanos).build()
    private fun javaUser(bytes: ByteArray) = Common.UserId.newBuilder().setValue(ByteString.copyFrom(bytes)).build()
    private fun javaMessageId(value: Long) = Model.MessageId.newBuilder().setValue(value).build()

    private fun wireTimestamp(offset: Long = 0) = ofEpochSecond(seconds + offset, nanos.toLong())
    private fun wireUser(bytes: ByteArray) = UserId(value_ = bytes.toByteString())

    @Test
    fun textMessageWithReactions() = assertCompatible(
        java = javaMessage(
            Model.Content.newBuilder().setText(
                Model.TextContent.newBuilder()
                    .setText("gm 👋 https://flipcash.com")
                    .addLinkPreviews(
                        Model.LinkPreview.newBuilder()
                            .setUrl("https://flipcash.com")
                            .setTitle("Flipcash")
                            .setDescription("Cash, but digital"),
                    ),
            ).build(),
        ),
        wire = wireMessage(
            Content(
                text = TextContent(
                    text = "gm 👋 https://flipcash.com",
                    link_previews = listOf(
                        LinkPreview(url = "https://flipcash.com", title = "Flipcash", description = "Cash, but digital"),
                    ),
                ),
            ),
        ),
        adapter = Message.ADAPTER,
        parse = Model.Message::parseFrom,
    )

    @Test
    fun replyMessage() = assertCompatible(
        java = javaMessage(
            Model.Content.newBuilder().setReply(
                Model.ReplyContent.newBuilder()
                    .setRepliedMessageId(javaMessageId(41))
                    .addContent(Model.Content.newBuilder().setText(Model.TextContent.newBuilder().setText("same"))),
            ).build(),
        ),
        wire = wireMessage(
            Content(
                reply = ReplyContent(
                    replied_message_id = MessageId(value_ = 41),
                    content = listOf(Content(text = TextContent(text = "same"))),
                ),
            ),
        ),
        adapter = Message.ADAPTER,
        parse = Model.Message::parseFrom,
    )

    private fun javaMessage(content: Model.Content): Model.Message {
        val javaReactor = Model.Reactor.newBuilder()
            .setUserId(javaUser(reactor))
            .setReactedTs(javaTimestamp(60))
            .setVersion(3)
            .setBoostUsd(0.25)
            .build()
        return Model.Message.newBuilder()
            .setMessageId(javaMessageId(42))
            .setSenderId(javaUser(sender))
            .addContent(content)
            .setTs(javaTimestamp())
            .setUnreadSeq(7)
            .setLastEditedTs(javaTimestamp(30))
            .setEventSequence(9_000_000_000L)
            .setReactions(
                Model.ReactionSummary.newBuilder()
                    .setMessageId(javaMessageId(42))
                    .addReactions(
                        Model.EmojiReaction.newBuilder()
                            .setEmoji(Model.Emoji.newBuilder().setValue("🔥"))
                            .setCount(2)
                            .setSelfReactor(javaReactor)
                            .addSampleReactors(javaReactor)
                            .setVersion(5)
                            .setBoostUsd(1.5),
                    ),
            )
            .setRedacted(true)
            .build()
    }

    private fun wireMessage(content: Content): Message {
        val wireReactor = Reactor(user_id = wireUser(reactor), reacted_ts = wireTimestamp(60), version = 3, boost_usd = 0.25)
        return Message(
            message_id = MessageId(value_ = 42),
            sender_id = wireUser(sender),
            content = listOf(content),
            ts = wireTimestamp(),
            unread_seq = 7,
            last_edited_ts = wireTimestamp(30),
            event_sequence = 9_000_000_000L,
            reactions = ReactionSummary(
                message_id = MessageId(value_ = 42),
                reactions = listOf(
                    EmojiReaction(
                        emoji = Emoji(value_ = "🔥"),
                        count = 2,
                        self_reactor = wireReactor,
                        sample_reactors = listOf(wireReactor),
                        version = 5,
                        boost_usd = 1.5,
                    ),
                ),
            ),
            redacted = true,
        )
    }

    private fun <J : com.google.protobuf.MessageLite, W : Any> assertCompatible(
        java: J,
        wire: W,
        adapter: ProtoAdapter<W>,
        parse: (ByteArray) -> J,
    ) {
        val javaBytes = java.toByteArray()
        val wireBytes = adapter.encode(wire)
        check(javaBytes.isNotEmpty()) { "the protobuf-java message encodes to nothing" }
        assertContentEquals(javaBytes, wireBytes, "protobuf-java and Wire encode differently")
        assertEquals(wire, adapter.decode(javaBytes), "Wire decodes protobuf-java's bytes to a different message")
        assertEquals(java, parse(wireBytes), "protobuf-java decodes Wire's bytes to a different message")
    }
}
