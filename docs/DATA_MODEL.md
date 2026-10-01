# Firestore model v1
All timestamps are Firestore server timestamps. No passwords, email, tokens, service credentials or private account data belong in public profiles.

## profiles/{uid}
Fields: displayName (1–60 chars), searchName (displayName.lower()), bio (0–160 chars). UID is document ID. Authenticated users can discover profiles; only owner creates/updates. Unknown fields rejected. Prefix-search query orders by searchName and uses startAt/endAt bounds. Profile existence must precede conversation creation.

## conversations/{pairId}
Fields: memberIds (exactly two distinct UIDs sorted lexicographically), createdAt. ID: sorted UID1 + ':' + UID2. Use standard Firebase-generated email/password UIDs. Membership and metadata immutable in MVP. Deterministic ID prevents duplicate conversations. If creation races, read existing record after conflict rather than overwriting it.
List query MUST include whereArrayContains("memberIds", currentUid); an unfiltered query is denied. Composite memberIds/createdAt descending index supplied. The discovery repository attaches one public-profile listener and one latest-message listener (createdAt descending, limit 1) per conversation, then sorts by latest message time with creation time as fallback. This small MVP observes all member conversations; pagination and listener cost optimization are deferred. Do not trust client-supplied sender identities or membership updates.

Search normalizes with Locale.ROOT, trims the input, debounces 300 ms, and returns up to 20 public profiles excluding self. It is name-prefix search, not substring/fuzzy search. Superseded responses cannot overwrite newer queries. No private email is queried or displayed.

## conversations/{pairId}/messages/{clientUuid}
Fields: senderId (must equal authenticated UID), text (non-whitespace, <=4000 chars), createdAt (server timestamp). Only participants can read/create. No updates/deletes in MVP. Stable client UUID supports reconciliation: after ambiguous timeout, read that ID to determine success before retrying; never overwrite an existing message.
Recent-history query: orderBy createdAt descending, limit 50; display sorted by server time then ID. Duplicate snapshot IDs collapse to one row. Pending writes use metadata and an estimated time only for provisional ordering; they display Sending rather than a confirmed timestamp. Pagination is deferred. Server indexes provide chronological queries; no email/credentials duplicated into messages.

The message repository reads an outgoing ID from the server before creating it. An existing matching sender/text means a previous attempt succeeded; a mismatched record is a conflict. Concurrent same-ID writes reconcile after immutable-rule denial. Offline preflight failure keeps the draft for retry; a write already queued by the SDK can await acknowledgement and is never labelled sent solely because of a timeout. Advanced durable outbox/reconnect behavior is deferred.

Rules default-deny all other paths. Tests cover anonymous/non-member access, sender spoofing, ownership, timestamp forgery, extra fields, pair constraints and immutable message IDs. Changes to schema require updated rules/tests together. Firestore rules are the authoritative boundary; Android validation improves UX only.
