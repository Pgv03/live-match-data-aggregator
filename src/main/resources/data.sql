MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('match-1001', 'Demo cricket match', 'provider-a');

MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-match-1001', 'match-1001');

MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-b', 'b-match-1001', 'match-1001');