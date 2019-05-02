import groovy.json.JsonOutput
import groovy.transform.Field


import java.util.Base64
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.slf4j.Logger
import org.slf4j.LoggerFactory



import groovy.json.JsonSlurper

import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityFind
import org.moqui.entity.EntityList
import org.moqui.entity.EntityValue


@Field Logger logger = LoggerFactory.getLogger('Team')

// api
void readTeam() {
    logger.info('readTeam')
    ExecutionContext ec = context.ec
    String teamPartyId = context.teamPartyId

    long now = System.currentTimeMillis()
    List<Object> payload = [now, emailAddress, partyId]
    String payloadJson = JsonOutput.toJson(payload)

    teamName = 'Mock Team'
    owner = [
        foo: 'bar',
    ]
    members = [
        [foo: 'bar'],
    ]
    teamPartyRoles = ['role1', 'role2']
}

// api
void updateTeamMember() {
    logger.info('updateTeamMember')

    String teamPartyId = context.teamPartyId
    String memberPartyId = context.memberPartyId
    Collection<String> roleTypes = context.roleTypes
}

// api
void deleteTeamMember() {
    logger.info('deleteTeamMember')

    String teamPartyId = context.teamPartyId
    String memberPartyId = context.memberPartyId
}
