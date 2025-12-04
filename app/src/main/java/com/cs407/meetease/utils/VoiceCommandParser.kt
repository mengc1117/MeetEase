package com.cs407.meetease.utils

import java.util.Locale

data class VoiceCommand(
    val durationSlots: Int? = null,
    val action: CommandAction? = null,
    val selectionNumber: Int? = null,
    val dayIndex: Int? = null,
    val timeSlot: Int? = null,
    val markAvailable: Boolean = false
)

enum class CommandAction {
    FIND_TIMES,
    SELECT_SUGGESTION,
    MARK_AVAILABILITY
}

class VoiceCommandParser {

    companion object {
        private val DURATION_PATTERNS = listOf(
            Regex("""(\d+)\s*hour[s]?"""),
            Regex("""(\d+)\s*hr[s]?"""),
            Regex("""(\d+)h"""),
            Regex("""(\d+)\s*minute[s]?"""),
            Regex("""(\d+)\s*min[s]?"""),
            Regex("""(\d+)m""")
        )

        private val ACTION_KEYWORDS = mapOf(
            "find" to CommandAction.FIND_TIMES,
            "search" to CommandAction.FIND_TIMES,
            "suggest" to CommandAction.FIND_TIMES,
            "show" to CommandAction.FIND_TIMES
        )

        private val SELECTION_KEYWORDS = listOf(
            "select", "choose", "pick", "confirm", "book", "take"
        )

        private val AVAILABILITY_KEYWORDS = mapOf(
            "available" to true,
            "free" to true,
            "mark" to true,
            "unavailable" to false,
            "busy" to false,
            "remove" to false
        )

        private val DAY_KEYWORDS = mapOf(
            "monday" to 0, "mon" to 0,
            "tuesday" to 1, "tue" to 1, "tues" to 1,
            "wednesday" to 2, "wed" to 2,
            "thursday" to 3, "thu" to 3, "thurs" to 3,
            "friday" to 4, "fri" to 4,
            "saturday" to 5, "sat" to 5,
            "sunday" to 6, "sun" to 6
        )

        private val TIME_KEYWORDS = mapOf(
            "morning" to 8,      // 8 AM
            "noon" to 12,        // 12 PM
            "afternoon" to 14,   // 2 PM
            "evening" to 18      // 6 PM
        )

        private val NUMBER_WORDS = mapOf(
            "first" to 1,
            "second" to 2,
            "third" to 3,
            "fourth" to 4,
            "fifth" to 5,
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5
        )
    }

    fun parse(speechText: String): VoiceCommand {
        // Normalize and fix common speech recognition errors
        val normalizedText = speechText.lowercase(Locale.getDefault())
            .replace(" to pm", " 2 pm")
            .replace(" to am", " 2 am")
            .replace(" too pm", " 2 pm")
            .replace(" too am", " 2 am")
            .replace(" for pm", " 4 pm")
            .replace(" for am", " 4 am")
            .replace(" ate pm", " 8 pm")
            .replace(" ate am", " 8 am")
        
        val availabilityInfo = parseAvailability(normalizedText)
        val selectionNumber = if (availabilityInfo == null) parseSelection(normalizedText) else null
        val durationSlots = if (availabilityInfo == null && selectionNumber == null) parseDuration(normalizedText) else null
        val action = parseAction(normalizedText, selectionNumber != null, availabilityInfo != null)

        return VoiceCommand(
            durationSlots = durationSlots,
            action = action,
            selectionNumber = selectionNumber,
            dayIndex = availabilityInfo?.first,
            timeSlot = availabilityInfo?.second,
            markAvailable = availabilityInfo?.third ?: false
        )
    }

    private fun parseAvailability(text: String): Triple<Int, Int, Boolean>? {
        // Check if this is an availability command
        val availabilityKeyword = AVAILABILITY_KEYWORDS.entries.firstOrNull { text.contains(it.key) }
        if (availabilityKeyword == null) return null

        val markAvailable = availabilityKeyword.value

        // Parse day
        val dayIndex = DAY_KEYWORDS.entries.firstOrNull { text.contains(it.key) }?.value
        if (dayIndex == null) return null

        // Parse time
        val timeSlot = parseTimeSlot(text)
        if (timeSlot == null) return null

        return Triple(dayIndex, timeSlot, markAvailable)
    }

    private fun parseTimeSlot(text: String): Int? {
        // Check for time keywords (morning, afternoon, etc.)
        for ((keyword, hour) in TIME_KEYWORDS) {
            if (text.contains(keyword)) {
                return (hour - 8) * 2 // Convert to slot index (8 AM = slot 0)
            }
        }

        // Check for specific times like "2 PM", "14:00", "2:30"
        val timePatterns = listOf(
            Regex("""(\d{1,2})\s*(?::|\.)\s*(\d{2})\s*(am|pm)"""),   // "2:30 PM", "2:30pm"
            Regex("""(\d{1,2})\s*(?::|\.)\s*(\d{2})"""),              // "14:00", "2:30" (24hr)
            Regex("""(\d{1,2})\s+(am|pm)"""),                         // "2 PM", "2 pm"
            Regex("""(\d{1,2})(am|pm)"""),                            // "2pm" (no space)
            Regex("""(\d{1,2})\s*o'?clock\s*(am|pm)?""")             // "2 o'clock", "2 o'clock pm"
        )

        for (pattern in timePatterns) {
            val match = pattern.find(text) ?: continue
            var hour = match.groupValues[1].toIntOrNull() ?: continue
            
            // Extract minute if present
            val minute = if (match.groupValues.size > 2 && match.groupValues[2].matches(Regex("""\d{2}"""))) {
                match.groupValues[2].toIntOrNull() ?: 0
            } else 0
            
            // Extract AM/PM
            val ampm = match.groupValues.find { it == "am" || it == "pm" } ?: ""

            // Convert to 24-hour format
            if (ampm == "pm" && hour != 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            
            // If no AM/PM specified and hour is small (1-7), assume PM for business hours
            if (ampm.isEmpty() && hour in 1..7) {
                hour += 12
            }

            // Convert to slot index (8 AM = slot 0)
            if (hour >= 8 && hour < 24) {
                val slotIndex = (hour - 8) * 2 + (minute / 30)
                return slotIndex
            }
        }

        return null
    }

    private fun parseSelection(text: String): Int? {
        // Check if user wants to select a suggestion
        val hasSelectionKeyword = SELECTION_KEYWORDS.any { text.contains(it) }
        
        if (!hasSelectionKeyword) return null

        // Look for number words (first, second, one, two, etc.)
        for ((word, number) in NUMBER_WORDS) {
            if (text.contains(word)) {
                return number
            }
        }

        // Look for digit (1, 2, 3, etc.)
        val digitMatch = Regex("""(\d+)""").find(text)
        if (digitMatch != null) {
            return digitMatch.groupValues[1].toIntOrNull()
        }

        // If just "select" or "choose" without number, default to first
        return 1
    }

    private fun parseDuration(text: String): Int? {
        // Try to match duration patterns
        for (pattern in DURATION_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: continue
                
                // Determine if it's hours or minutes
                return if (text.contains("hour") || text.contains("hr") || Regex("""\d+h""").containsMatchIn(text)) {
                    value * 2 // Convert hours to slots (2 slots per hour)
                } else {
                    value / 30 // Convert minutes to slots (30 min per slot)
                }
            }
        }

        // Check for written numbers
        val hourWords = mapOf(
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "half" to 1 // half hour = 1 slot
        )

        for ((word, value) in hourWords) {
            if (text.contains("$word hour")) {
                return value * 2
            }
        }

        if (text.contains("half hour") || text.contains("thirty minutes")) {
            return 1
        }

        return null
    }

    private fun parseAction(text: String, hasSelection: Boolean, hasAvailability: Boolean): CommandAction? {
        // Prioritize availability marking
        if (hasAvailability) {
            return CommandAction.MARK_AVAILABILITY
        }

        // If selection detected, prioritize SELECT_SUGGESTION
        if (hasSelection) {
            return CommandAction.SELECT_SUGGESTION
        }

        // Check for action keywords
        for ((keyword, action) in ACTION_KEYWORDS) {
            if (text.contains(keyword)) {
                // "find" with "time" or "meeting" suggests FIND_TIMES
                if (keyword == "find" && (text.contains("time") || text.contains("meeting"))) {
                    return CommandAction.FIND_TIMES
                }
                return action
            }
        }

        return null
    }
}
