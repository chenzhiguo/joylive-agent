/*
 * Copyright © ${year} ${owner} (${email})
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.live.agent.implement.service.policy.nacos;

import com.jd.live.agent.core.config.SyncConfig;
import com.jd.live.agent.core.extension.annotation.ConditionalOnProperty;
import com.jd.live.agent.core.extension.annotation.Extension;
import com.jd.live.agent.core.inject.annotation.Config;
import com.jd.live.agent.core.inject.annotation.Injectable;
import com.jd.live.agent.core.util.Close;
import com.jd.live.agent.core.util.template.Template;
import com.jd.live.agent.governance.config.GovernanceConfig;
import com.jd.live.agent.governance.policy.live.LiveSpace;
import com.jd.live.agent.governance.service.sync.AbstractLiveSpaceSubscriptionSyncer;
import com.jd.live.agent.governance.service.sync.SyncKey;
import com.jd.live.agent.governance.service.sync.Syncer;
import com.jd.live.agent.governance.service.sync.api.ApiSpace;
import com.jd.live.agent.implement.service.policy.nacos.client.NacosClient;
import com.jd.live.agent.implement.service.policy.nacos.client.NacosSyncKey;
import com.jd.live.agent.implement.service.policy.nacos.config.NacosSyncConfig;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jd.live.agent.implement.service.policy.nacos.LiveSpaceNacosSyncer.NacosLiveSpaceKey;

/**
 * LiveSpaceSyncer is responsible for synchronizing live spaces from nacos.
 */
@Injectable
@Extension("LiveSpaceNacosSyncer")
@ConditionalOnProperty(name = SyncConfig.SYNC_LIVE_SPACE_TYPE, value = "nacos")
@ConditionalOnProperty(value = GovernanceConfig.CONFIG_LIVE_ENABLED, matchIfMissing = true)
public class LiveSpaceNacosSyncer extends AbstractLiveSpaceSubscriptionSyncer<NacosLiveSpaceKey> {

    @Config(SyncConfig.SYNC_LANE_SPACE)
    private NacosSyncConfig syncConfig = new NacosSyncConfig();

    private NacosClient client;

    @Override
    protected void startSync() throws Exception {
        client = new NacosClient(syncConfig);
        client.connect();
        super.startSync();
    }

    @Override
    protected void stopSync() {
        Close.instance().close(client);
        super.stopSync();
    }

    @Override
    protected Template createTemplate() {
        return new Template(syncConfig.getLiveSpaceKeyTemplate());
    }

    @Override
    protected NacosLiveSpaceKey createSpacesKey() {
        return new NacosLiveSpaceKey(null, syncConfig.getLiveSpacesKey(), syncConfig.getLiveSpaceGroup());
    }

    @Override
    protected NacosLiveSpaceKey createSpaceKey(String spaceId) {
        Map<String, Object> context = new HashMap<>();
        context.put("id", spaceId);
        String dataId = template.evaluate(context);
        return new NacosLiveSpaceKey(spaceId, dataId, syncConfig.getLiveSpaceGroup());
    }

    @Override
    protected Syncer<NacosLiveSpaceKey, List<ApiSpace>> createSpacesSyncer() {
        return client.createSyncer(this::parseSpaces);
    }

    @Override
    protected Syncer<NacosLiveSpaceKey, LiveSpace> createSyncer() {
        return client.createSyncer(this::parseSpace);
    }

    @Getter
    protected static class NacosLiveSpaceKey extends SyncKey.LiveSpaceKey implements NacosSyncKey {

        private final String dataId;

        private final String group;

        public NacosLiveSpaceKey(String id, String dataId, String group) {
            super(id);
            this.dataId = dataId;
            this.group = group;
        }
    }
}
