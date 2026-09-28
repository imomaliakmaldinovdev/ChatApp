# Firestore model v1
All timestamps are Firestore server timestamps. No passwords, email, tokens, service credentials or private account data belong in public profiles.

## profiles/{uid}
Fields: displayName (1–60 chars), searchName (displayName.lower()), bio (0–160 chars). UID is document ID. Authenticated users can discover profiles; only owner creates/updates. Unknown fields rejected. Prefix-search query orders by searchName and uses startAt/endAt bounds. Profile existence must precede conversation creation.

## conversations/{pairId}
Fields: memberIds (exactly two distinct UIDs sorted lexicographically), createdAt. ID: sorted UID1 + ':' + UID2. Use standard Firebase-generated email/password UIDs. Membership and metadata immutable in MVP. Deterministic ID prevents duplicate conversations. If creation races, read existing record after conflict rather than overwriting it.
List query MUST include whereArrayContains("memberIds", currentUid); an unfiltered query is denied. Composite memberIds/createdAt descending index supplied. Latest-message previews/activity ordering require Wednesday repository work, not yet implemented. Do not trust client-supplied sender identities or membership updates.

## conversations/{pairId}/messages/{clientUuid}
Fields: senderId (must equal authenticated UID), text (non-whitespace, <=4000 chars), createdAt (server timestamp). Only participants can read/create. No updates/deletes in MVP. Stable client UUID supports reconciliation: after ambiguous timeout, read that ID to determine success before retrying; never overwrite an existing message.
Recent-history query: orderBy createdAt descending, limit 50; reverse for display. Pagination deferred. Server indexes provide chronological queries; no email/credentials duplicated into messages.

Rules default-deny all other paths. Tests cover anonymous/non-member access, sender spoofing, ownership, timestamp forgery, extra fields, pair constraints and immutable message IDs. Changes to schema require updated rules/tests together. Firestore rules are the authoritative boundary; Android validation improves UX only.
