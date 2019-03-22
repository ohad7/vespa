// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/hitrank.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vector>

namespace search::engine {

class SearchReply
{
public:
    using UP = std::unique_ptr<SearchReply>;

    class Hit
    {
    public:
        Hit() : gid(), metric(0), path(0), _distributionKey(0) {}
        void setDistributionKey(uint32_t key) { _distributionKey = key; }
        uint32_t getDistributionKey() const { return _distributionKey; }
        document::GlobalId gid;
        search::HitRank    metric;
        uint32_t           path; // wide
    private:
        int32_t            _distributionKey; // wide
    };

    class Coverage {
    public:
        Coverage() : Coverage(0) { }
        Coverage(uint64_t active) : Coverage(active, active) { }
        Coverage(uint64_t active, uint64_t covered)
            : _covered(covered), _active(active), _soonActive(active),
              _degradeReason(0), _nodesQueried(1), _nodesReplied(1)
        { }
        uint64_t getCovered() const { return _covered; }
        uint64_t getActive() const { return _active; }
        uint64_t getSoonActive() const { return _soonActive; }
        uint32_t getDegradeReason() const { return _degradeReason; }
        uint16_t getNodesQueried() const { return _nodesQueried; }
        uint16_t getNodesReplied() const { return _nodesReplied; }
        bool wasDegradedByMatchPhase() const { return ((_degradeReason & MATCH_PHASE) != 0); }
        bool wasDegradedByTimeout() const { return ((_degradeReason & TIMEOUT) != 0); }

        Coverage & setCovered(uint64_t v) { _covered = v; return *this; }
        Coverage & setActive(uint64_t v) { _active = v; return *this; }
        Coverage & setSoonActive(uint64_t v) { _soonActive = v; return *this; }
        Coverage & setDegradeReason(uint32_t v) { _degradeReason = v; return *this; }
        Coverage & setNodesQueried(uint16_t v) { _nodesQueried = v; return *this; }
        Coverage & setNodesReplied(uint16_t v) { _nodesReplied = v; return *this; }

        Coverage & degradeMatchPhase() { _degradeReason |= MATCH_PHASE; return *this; }
        Coverage & degradeTimeout() { _degradeReason |= TIMEOUT; return *this; }
        Coverage & degradeAdaptiveTimeout() { _degradeReason |= ADAPTIVE_TIMEOUT; return *this; }
        enum DegradeReason {MATCH_PHASE=0x01, TIMEOUT=0x02, ADAPTIVE_TIMEOUT=0x04};
    private:
        uint64_t _covered;
        uint64_t _active;
        uint64_t _soonActive;
        uint32_t _degradeReason;
        uint16_t _nodesQueried;
        uint16_t _nodesReplied;
    };

    // set to false to indicate 'talk to the hand' behavior
    bool                  valid;

    // normal results
    uint32_t              offset;
private:
    uint32_t _distributionKey;
public:
    uint64_t              totalHitCount;
    search::HitRank       maxRank;
    std::vector<uint32_t> sortIndex;
    std::vector<char>     sortData;
    vespalib::Array<char> groupResult;
    Coverage              coverage;
    bool                  useWideHits;
    std::vector<Hit>      hits;
    PropertiesMap         propertiesMap;

    // in case of error
    uint32_t              errorCode;
    vespalib::string      errorMessage;

    SearchRequest::UP     request;

    SearchReply();
    ~SearchReply();
    SearchReply(const SearchReply &rhs); // for test only
    
    void setDistributionKey(uint32_t key) { _distributionKey = key; }
    uint32_t getDistributionKey() const { return _distributionKey; }
};

}

