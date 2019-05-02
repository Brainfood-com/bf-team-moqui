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


@Field Logger logger = LoggerFactory.getLogger('Invite')

// api
void createInvite() {
    logger.info('createInvite')
    ExecutionContext ec = context.ec

    String httpHostname = context.httpHostname
    String httpScheme = context.httpScheme
    String firstName = context.firstName
    String lastName = context.lastName
    String emailAddress = context.emailAddress

    workEffortId = 'foo'
    currentStatusId = 'bar'
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
