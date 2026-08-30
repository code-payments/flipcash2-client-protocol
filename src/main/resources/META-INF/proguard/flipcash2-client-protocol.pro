# R8/ProGuard rules for the generated protobuf messages in this artifact.
#
# protobuf-javalite ships no keep rules of its own, so without this every consumer
# app has to write it. javalite resolves fields reflectively: the schema built from
# newMessageInfo looks up java.lang.reflect.Field by the generated `<name>_` field.
# Methods and builders are reached from ordinary call sites, so R8 traces them
# without help and they are deliberately not kept here.
#
# -keepclassmembers does not keep the class, so a message type nothing references
# is still removed entirely. The rule only applies to messages that survive on
# their own merit.
#
# Deliberately not scoped to this artifact's own package. The well-known types
# (Any, Timestamp, Duration, Struct) come from protobuf-javalite itself, and other
# dependencies ship generated messages with no rules of their own; a rule scoped to
# the generated package would leave those broken under R8 full mode.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
