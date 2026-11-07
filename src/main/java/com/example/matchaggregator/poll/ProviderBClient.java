package com.example.matchaggregator.poll;

public interface ProviderBClient {
    /** Returns the parsed score response, or throws ProviderException. */
    ProviderBScore fetchScore(String providerMatchId);

    /** Returns the parsed commentary response, or throws ProviderException. */
    ProviderBCommentary fetchCommentary(String providerMatchId);
}