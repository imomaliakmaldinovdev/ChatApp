# Scope and design reference
Reference: Chatvia Light, https://themesbrand.com/chatvia/layouts/index.html
Authentication reference: https://themesbrand.com/chatvia/ (login/register examples).
This is inspiration for a native Android implementation, not copied template code/assets.

Week-one goal: managed email/password auth, a basic chats list, read-only public profile, user search, one-to-one text conversations, realtime receive, recent persisted history, timestamps and essential error/access tests.
Defer presence, read receipts/unread badges, profile editing, settings/themes, pagination, advanced reconnect behavior, calls, groups and attachments.

Monday: runnable scaffold, basic routing/service contracts, data model and tested access rules, local backend setup, GitFlow guide. Cloud connection requires a Firebase project supplied/created by the owner.

Android adaptation: single-pane phone navigation (list -> conversation -> Back); side-by-side reference is not required. Use Material form components, purple accent #7265E6, pale background #F7F7FC, dark text #24243D, 24dp page inset. Keep touch targets >=48dp, labels/focus accessible, text scalable, keyboard insets accounted for in messaging screens. The implemented landing layout handles system insets and scrolls with large text.

Planned screen states: login/register validation, submitting/failure/success; chats loading/empty/error/content; search loading/no results/error/results; chat loading/empty/history/pending-send/failure; profile missing-field fallbacks. Network failures must never masquerade as empty successful results.

Budget: 40 hours, 8/day, using managed services and familiar components. Original 120-hour requirements are preserved as later scope on Trello. This is a reduced MVP target, not the entire original product.
