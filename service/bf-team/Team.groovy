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
@Field Timestamp now = context.ec.user.nowTimestamp
//@Field String currentUserPartyId = context.partyId || ec.user.userAccount?.partyId || 'alyvr-admin'
@Field String currentUserPartyId = 'alyvr-admin'

void ensureTeamMemberRoles() {
    ExecutionContext ec = context.ec
    String memberPartyId = context.memberPartyId
    Collection<String> roleTypes = context.roleTypes
    for (String roleTypeId: roleTypes) {
        ec.service.sync().name('ensure', 'PartyRole').parameters([partyId: memberPartyId, roleTypeId: roleTypeId]).call()
    }
}

/*
Team[PartyGroup]
PartyRelationship
    relationshipTypeEnumId = 'PrtMember'
    fromPartyId = Team[PartyGroup]
    fromRoleTypeId = 'OrgTeam'
    toPartyId = Member[Party*]
    toRoleTypeId = ''
        'Owner'
        'Administrator'
        'Manager'
        'Reporter'
        'Subscriber'
        'Contractor'
    statusId = ''
*/ 

// internal
public Map<String, Object> createTeam() {
    logger.info('createTeam')
    ExecutionContext ec = context.ec
    String teamName = context.teamName
    String ownerPartyId = context.ownerPartyId

    EntityValue teamParty = ec.entity.makeValue('mantle.party.Party', [partyTypeEnumId: 'PtyOrganization']).setSequencedIdPrimary().create()
    String teamPartyId = teamParty.partyId
    ec.entity.makeValue('mantle.party.PartyRole', [partyId: teamPartyId, roleTypeId: 'OrgTeam']).create()
    ec.service.sync().name('ensure', 'PartyRole').parameters([partyId: ownerPartyId, , roleTypeId: 'Owner']).call()
    ec.entity.makeValue('mantle.party.PartyRelationship', [
        relationshipTypeEnumId: 'PrtMember',
        fromPartyId: teamPartyId,
        fromRoleTypeId: 'OrgTeam',
        toPartyId: ownerPartyId,
        toRoleTypeId: 'Owner',
        fromDate: now,
    ]).setSequencedIdPrimary().create()
    return [partyId: teamPartyId]
    // TODO?: Add a per-install ECA to attach the team to the top-level organization
/*
    PtyOrganization
    <mantle.party.Party partyId="ORG_ZIZI_HR" pseudoId="ZIHR" partyTypeEnumId="PtyOrganization" ownerPartyId="ORG_ZIZI_HR"/>
    <mantle.party.Organization partyId="ORG_ZIZI_HR" organizationName="Ziziwork Human Resources"/>
    <mantle.party.PartyRole partyId="ORG_ZIZI_HR" roleTypeId="OrgInternal"/>
    <mantle.party.PartyRelationship partyRelationshipId="ORG_ZIZI_HR" relationshipTypeEnumId="PrtOrgRollup"
            fromPartyId="ORG_ZIZI_HR" fromRoleTypeId="OrgInternal" toPartyId="ORG_ZIZI_CORP" toRoleTypeId="OrgInternal"
            fromDate="1265184000000"/>
*/
}

protected void setRoleDescriptions(Map<String, Map<String, String>> roleTypeDescriptions) {
    ExecutionContext ec = context.ec
    for (Map.Entry<String, Map<String, String>> roleDescEntry: roleTypeDescriptions.entrySet()) {
        EntityValue roleType = ec.entity.find('mantle.party.RoleType').condition('roleTypeId', roleDescEntry.getKey()).one()
        roleDescEntry.value = [roleTypeId: roleType.roleTypeId, description: roleType.description]
    }
}

protected List<Map<String, Object>> readTeamPartyRoles(String teamPartyId, String optionalMemberPartyId, boolean resolve) {
    ExecutionContext ec = context.ec

    Map<String, String> searchContext = [
        relationshipTypeEnumId: 'PrtMember',
        fromPartyId: teamPartyId,
        fromRoleTypeId: 'OrgTeam',
    ]
    if (optionalMemberPartyId != null) {
        searchContext.toPartyId = optionalMemberPartyId
    }

    EntityFind relations = ec.entity.find('mantle.party.PartyRelationship').condition([
        relationshipTypeEnumId: 'PrtMember',
        fromPartyId: teamPartyId,
        fromRoleTypeId: 'OrgTeam',
    ]).conditionDate('fromDate', 'thruDate', now)
    Map<String, Set<String>> memberToRoles = [:]
    Map<String, Map<String, String>> roleTypeDescriptions = [:]
    for (EntityValue relation: relations.list()) {
        String memberPartyId = relation.toPartyId
        String memberRoleTypeId = relation.toRoleTypeId
        if (resolve) {
            roleTypeDescriptions[memberRoleTypeId] = null
        }
        Set<String> memberRoles = memberToRoles[memberPartyId]
        if (!memberRoles) {
            memberToRoles[memberPartyId] = memberRoles = new HashSet<>()
        }
        memberRoles.add(memberRoleTypeId)
    }

    if (resolve) {
        setRoleDescriptions(roleTypeDescriptions)
    }
    return memberToRoles.entrySet().collect { Map.Entry<String, Set<String>> entry ->
        return [
            memberPartyId: entry.key,
            roleTypes: entry.value.collect { String roleTypeId ->
                return resolve ? roleTypeDescriptions[roleTypeId] : roleTypeId
            }
        ]
    }
}

protected List<Map<String, Object>> loadTeamMembers(String teamPartyId) {
    return readTeamPartyRoles(teamPartyId, null, true)
}

public Map<String, Object> readTeamPartyRoles() {
    ExecutionContext ec = context.ec
    String teamPartyId = context.partyId

    List<Map<String, Object>> userPartyRoles = readTeamPartyRoles(teamPartyId, currentUserPartyId, false)
    if (userPartyRoles) {
        return [teamPartyRoles: userPartyRoles[0].roleTypes]
    } else {
        return [teamPartyRoles: []]
    }
}

// rest api
public Map<String, Object> readTeam() {
    logger.info('readTeam')
    Map<String, Object> simpleTeamResult = readSimpleTeam()

    Map<String, Object> team = simpleTeamResult.team
    team.partyTypeEnumId = 'Team'
    team.owner = null
    team.members = loadTeamMembers(teamPartyId)
    return [team: team]
}

// rest api
public Map<String, Object> readSimpleTeam() {
    logger.info('readSimpleTeam')
    ExecutionContext ec = context.ec
    String teamPartyId = context.partyId
    logger.info("teamPartyId=" + teamPartyId)

    EntityValue teamOrganization = ec.entity.find('mantle.party.Organization').condition('partyId', teamPartyId).useCache(false).one()
    EntityValue profilePicValue = ec.entity.find('mantle.party.PartyContent').condition([
        partyId: teamPartyId,
        partyContentTypeEnumId: 'PcntPrimaryImage',
    ]).one()

    Map<String, Object> readTeamPartyRolesResult = ec.service.sync().name('bf-team.TeamServices', 'read', 'TeamPartyRoles').parameter('partyId', teamPartyId).call()
    return [
        team: [
            partyId: teamPartyId,
            teamName: teamOrganization.organizationName,
            profilePic: profilePicValue?.contentLocation, 
            //owner: null,
            userPartyRoles: readTeamPartyRolesResult.teamPartyRoles,
        ],
    ]
}

// rest api
public Map<String, Object> setTeamMember() {
    logger.info('createTeamMember')

    String teamPartyId = context.partyId
    String memberPartyId = context.memberPartyId
    Collection<String> roleTypes = context.roleTypes || []

    ensureTeamMemberRoles()

    // create rows
    for (String roleTypeId: roleTypes) {
        Map<String, Object> relRoleSearch = [
            relationshipTypeEnumId: 'PrtMember',
            fromPartyId: teamPartyId,
            fromRoleTypeId: 'OrgTeam',
            toPartyId: memberPartyId,
            toRoleTypeId: roleTypeId,
        ]
        EntityFind existingFind = ec.entity.find('mantle.party.PartyRelationship').condition(relRoleSearch).conditionDate('fromDate', 'thruDate', now)
        if (existingFind.count() == 0) {
            ec.entity.makeValue('mantle.party.PartyRelationship', relSearch).set('fromDate', now).setSequencedIdPrimary().create()
        }
    }

    // delete rows
    Map<String, Object> relRemSearch = [
        relationshipTypeEnumId: 'PrtMember',
        fromPartyId: teamPartyId,
        fromRoleTypeId: 'OrgTeam',
        toPartyId: memberPartyId,
    ]
    EntityFind remFind = ec.entity.find('mantle.party.PartyRelationship').condition(relRoleSearch).conditionDate('fromDate', 'thruDate', now)
    for (EntityValue remValue: remFind.list()) {
        String toRoleTypeId = remValue.toRoleTypeId
        if (!roleTypes.contains(remValue.toRoleTypeId)) {
            remValue.thruDate = now
            remValue.update()
        }
    }
    return [:]
}

// rest api
public Map<String, Object> readAllTeams() {
    logger.info('readAllTeams')

    ExecutionContext ec = context.ec
    logger.info('-> user=' + ec.user.userAccount)

    Map<String, Object> relRoleSearch = [
        relationshipTypeEnumId: 'PrtMember',
        fromRoleTypeId: 'OrgTeam',
        toPartyId: currentUserPartyId,
    ]
    EntityFind teamFind = ec.entity.find('mantle.party.PartyRelationship').condition(relRoleSearch).conditionDate('fromDate', 'thruDate', now)
    List<Map<String, Object>> teams = []
    for (EntityValue teamValue: teamFind.list()) {
        Map<String, Object> readTeamResult = ec.service.sync().name('bf-team.TeamServices', 'read', 'SimpleTeam').parameter('partyId', teamValue.fromPartyId).call()
        teams.add(readTeamResult.team)
	}
	return [teams: teams]
}
