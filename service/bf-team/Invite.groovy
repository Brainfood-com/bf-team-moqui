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
import org.moqui.resource.ResourceReference

@Field Logger logger = LoggerFactory.getLogger('Invite')
@Field Timestamp now = context.ec.user.nowTimestamp

private EntityFind createDynamicView() {
    logger.info('createDynamicView')
    EntityFind ef = ec.entity.find('WorkEffort')
    logger.info('ef=' + ef)
    ef.makeEntityDynamicView()
        .addMemberEntity('we', 'WorkEffort', null, null, null)
        .addMemberEntity('wecm', 'WorkEffortContactMech', 'we', false, [workEffortId: 'workEffortId'])
        .addMemberEntity('cm', 'ContactMech', 'wecm', false, [contactMechId: 'contactMechId'])
        .addAlias('we', 'workEffortId')
        .addAlias('we', 'workEffortName')
        .addAlias('we', 'statusId')
        .addAlias('wecm', 'fromDate')
        .addAlias('wecm', 'thruDate')
        .addAlias('wecm', 'contactMechPurposeId')
        .addAlias('cm', 'infoString')
        .addAlias('cm', 'contactMechTypeEnumId')
    logger.info('ef=' + ef)
    return ef
}

// api
Map<String, Object> inviteUser() {
    logger.info('inviteUser')
    ExecutionContext ec = context.ec

    String httpHostname = context.httpHostname
    String httpScheme = context.httpScheme
    String firstName = context.firstName
    String lastName = context.lastName
    String emailAddress = context.emailAddress

    EntityFind ef = createDynamicView()
    ef
        .condition([
            workEffortName: 'TEAM_MEMBER_INVITE',
            workEffortTypeEnumId: 'BfTeamInvite',
            //statusId: 'WeInProgress',
            contactMechPurposeId: 'EmailPrimary',
            contactMechTypeEnumId: 'CmtEmailAddress',
            infoString: emailAddress,
        ])
        .conditionDate('fromDate', 'thruDate', now)

    EntityList workEfforts = ef.list()
    String workEffortId
    if (workEfforts.isEmpty()) {
        Map<String, Object> cweResult = ec.service.sync().name('create', 'mantle.work.effort.WorkEffort').parameters([
            workEffortName: 'TEAM_MEMBER_INVITE',
            workEffortTypeEnumId: 'BfTeamInvite',
            statusId: 'WeInProgress',
        ]).call()
        workEffortId = cweResult.workEffortId
        Map<String, Object> ceaResult = ec.service.sync().name('mantle.party.ContactServices.create#EmailAddress').parameters([
            emailAddress: emailAddress,
        ]).call()
        String contactMechId = ceaResult.contactMechId
        ec.service.sync().name('create', 'mantle.work.effort.WorkEffortContactMech').parameters([
			workEffortId: workEffortId,
            contactMechId: contactMechId,
            contactMechPurposeId: 'EmailPrimary',
        ]).call()
        ResourceReference jsonDataRef = ec.resource.getLocationReference('dbresource://bf-team/work/invite/' + workEffortId + '.json')
        jsonDataRef.putText(JsonOutput.toJson([
            httpHostname: httpHostname,
            httpScheme: httpScheme,
            newUserEmailAddress: emailAddress,
            newUserFirstName: firstName,
            newUserLastName: lastName,
        ]))
        ec.service.sync().name('create', 'mantle.work.effort.WorkEffortContent').parameters([
			workEffortId: workEffortId,
			contentLocation: jsonDataRef.getLocation(),
            contentTypeEnumId: 'BfTeamInviteMetaData',
        ]).call()
    } else {
        workEffortId = workEfforts[0].workEffortId
    }
/*
    emailAddress -> WorkEffortContactMech

    attributes -> WorkEffortContent
        contentTypeEnumId = WorkEffortAttribute
        contentLocation = $name
        description = $value
*/
    return [
        workEffortId: workEffortId,
        //statusId: 'PENDING',
    ]
}

// This is run via eeca on WorkEffortContactMech[EmailPrimary]
Map<String, Object> checkExistingUser() {
    logger.info('checkExistingUser')
    ExecutionContext ec = context.ec

    String partyId = context.partyId
    String workEffortId = context.workEffortId

    EntityFind ef = createDynamicView()
    ef.condition([
        workEffortId: workEffortId,
        workEffortName: 'TEAM_MEMBER_INVITE',
        workEffortTypeId: 'BfTeamInvite',
        //statusId: 'WeInProgress',

        contactMechPurposeId: 'EmailPrimary',
        contactMechTypeEnumId: 'EmailAddress',
    ])
    .conditionDate('fromDate', 'thruDate', now)

    EntityValue wecm = ef.list().getFirst()
    logger.info('wecm=' + wecm)
    if (wecm == null) {
        return [:]
    }

    String emailAddress = wecm.infoString


    // FIXME: study UserAccount, perhaps use bf-auth-moqui instead
    /*
    EntityValue userLogin = delegator.findOne('UserLogin', [userLoginId: emailAddress.toLowerCase()], false)
    String partyId = userLogin?.partyId
    if (partyId) {
        dispatcher.runSync('updateWorkEffort', [
            userLogin: context.userLogin,
            workEffortId: workEffortId,
            currentStatusId: 'PARTYINV_ACCEPTED',
        ])
        dispatcher.runSync('assignPartyToWorkEffort', [
            userLogin: context.userLogin,
            workEffortId: weaa.workEffortId,
            partyId: partyId,
            roleTypeId: 'PERSON_ROLE',
            statusId: 'PARTY_ENABLED',
        ])
    }
    */
    return [:]
}

Map<String, Object> attachInviteToOwnerTeam() {
    logger.info('attachInviteToOwnerTeam')
    ExecutionContext ec = context.ec

    String partyId = context.partyId
    String workEffortId = context.workEffortId

    logger.info('partyId=' + partyId + ', workEffortId=' + workEffortId)
    if (!partyId) {
        return [:]
    }

    EntityList existingParties = ec.entity.find('mantle.work.effort.WorkEffortParty').condition([
        workEffortId: workEffortId,
        partyId: partyId,
        roleTypeId: 'Owner',
    ]).conditionDate('fromDate', 'thruDate', now).list()
    if (existingParties.isEmpty()) {
        ec.service.sync().name('create', 'mantle.work.effort.WorkEffortParty').parameters([
            workEffortId: workEffortId,
            partyId: partyId,
            roleTypeId: 'Owner',
            fromDate: now,
            statusId: 'WeptAssigned',
        ]).call()
    }

    return [:]
}

// api
void updateInvite() {
    logger.info('updateInvite')

    String workEffortId = context.workEffortId
    String firstName = context.firstName
    String lastName = context.lastName
    String emailAddress = context.emailAddress
    String currentStatusId = context.currentStatusId
}

// api
void readInvites() {
    logger.info('readInvites')

    workEfforts = [
        [
            workEffortId: 'foo',
            currentStatusId: 'bar',
        ],
    ]
}
