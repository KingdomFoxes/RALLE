# Session chat destinations

Chat tabbing retains at most 128 distinct valid DM contacts per connection.
Incoming and outgoing DMs refresh a contact's recency; when a new contact exceeds
the bound, the least recently exchanged-with contact is removed. Retained tabs
keep first-seen order and canonical spelling, without case-insensitive duplicates.
Only that bounded contact set participates in tab-cycle construction.

Incoming messages never change the selected channel or draft destination. An
already selected DM destination remains intact even if its tab is later evicted;
explicitly cycling from such a stale tab goes to All, matching the existing
stale-destination behavior. Disconnect clears both contact order and recency.
Nothing is persisted, auto-sent or inferred from unrelated commands.

`ChatTypeTabServiceTest` exercises 10,000 contacts, the 128-entry cap, refreshed
recency, retained first-seen order, case deduplication, unchanged open destinations
and disconnect clearing alongside the existing channel/draft tests.
