MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('match-1001', 'Demo cricket match', 'provider-a');

MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-match-1001', 'match-1001');

MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-b', 'b-match-1001', 'match-1001');

MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('demo-ordered', 'Demo: ordered delivery', 'provider-a');
MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('demo-duplicate', 'Demo: duplicate delivery', 'provider-a');
MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('demo-out-of-order', 'Demo: out-of-order commentary', 'provider-a');
MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('demo-delayed-snapshot', 'Demo: delayed older snapshot', 'provider-a');
MERGE INTO matches (id, title, primary_provider) KEY (id)
    VALUES ('demo-correction', 'Demo: corrections', 'provider-a');

MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-demo-ordered', 'demo-ordered');
MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-demo-duplicate', 'demo-duplicate');
MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-demo-out-of-order', 'demo-out-of-order');
MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-demo-delayed-snapshot', 'demo-delayed-snapshot');
MERGE INTO provider_match_map (provider, provider_match_id, match_id) KEY (provider, provider_match_id)
    VALUES ('provider-a', 'a-demo-correction', 'demo-correction');