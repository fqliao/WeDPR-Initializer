pragma solidity ^0.4.24;

// Interface contract exporting WeDRP precompiled APIs.
// Please do not modify this contract unless you have updated the builtin
// precompiled contract implementation in the blockchain node executable.
contract WedprPrecompiled {
    /// hidden asset suite functions

    // Verifies issued credit data used in hidden asset suite.
    function hiddenAssetVerifyIssuedCredit(string issueArgument)
        public returns(string currentCredit, string creditStorage);

    // Verifies fulfilled credit data used in hidden asset suite.
    function hiddenAssetVerifyFulfilledCredit(string fulfillArgument)
        public returns(string currentCredit, string creditStorage);

    // Verifies transferred credit data used in hidden asset suite.
    function hiddenAssetVerifyTransferredCredit(string transferRequest)
        public returns(string spentCurrentCredit, string spentCreditStorage,
                       string newCurrentCredit, string newCreditStorage);

    // Verifies split credit data used in hidden asset suite.
    function hiddenAssetVerifySplitCredit(string splitRequest)
        public returns(string spentCurrentCredit, string spentCreditStorage,
                       string newCurrentCredit1, string newCreditStorage1,
                       string newCurrentCredit2, string newCreditStorage2);
    
    /// anonymous voting suite functions

    // Verifies bounded voting verify vote request used in anonymous voting suite.
    function anonymousVotingVerifyBoundedVoteRequest(string systemParameters, string voteRequest)
        public returns(string blankBallot, string voteStoragePart);

    // Verifies unbounded voting verify vote request used in anonymous voting suite.
    function anonymousVotingVerifyUnboundedVoteRequest(string systemParameters, string voteRequest)
        public returns(string blankBallot, string voteStoragePart);

    // Aggregates vote sum response used in anonymous voting suite.
    function anonymousVotingAggregateVoteSumResponse(string systemParameters, string voteStoragePart, string voteStorage)
        public returns(string voteStorageSum);

    // Aggregates hPoint in anonymous voting suite.
    function anonymousVotingAggregateHPoint(string hPointShare, string hPointSum)
        public returns(string newHPointSum);

    // Verifies count request used in anonymous voting suite.
    function anonymousVotingVerifyCountRequest(string systemParameters, string voteStorage, string hPointShare, string decryptedRequest)
        public returns(string counterId, string decryptedResultPartStoragePart);

    // Aggregates decrypted part sum used in anonymous voting suite.
    function anonymousVotingAggregateDecryptedPartSum(string systemParameters, string decryptedResultPartStoragePart, string decryptedResultPartStorage)
        public returns(string decryptedResultPartStorageSum);

    // Verifies vote result used in anonymous voting suite.
    function anonymousVotingVerifyVoteResult(string systemParameters, string voteStorageSum, string decryptedResultPartStorageSum, 
        string voteResultRequest) public returns(int); 

    // Gets vote result used in anonymous voting suite.
    function anonymousVotingGetVoteResultFromRequest(string voteResultRequest) public returns(string voteResult);

    /// anonymous auction suite functions

    // Verifies bid request in anonymous auction suite.
    function anonymousAuctionVerifyBidSignatureFromBidRequest(string bidRequest) public returns(string bidStorage); 
    
    // Verifies bid comparison request in anonymous auction suite.
    function anonymousAuctionVerifyBidSignatureFromBidComparisonRequest(string bidComparisonRequest) public; 

    // Verifies winner in anonymous auction suite.
    function anonymousAuctionVerifyWinner(string winnerClaimRequest, string allBidStorageRequest) public returns(int bid_value, string public_key);
}
