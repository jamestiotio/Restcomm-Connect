/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.restcomm.connect.dao.entities;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;

/**
 * @author <a href="mailto:n.congvu@gmail.com">vunguyen</a>
 */

@Immutable
public class TranscriptionFilter {
    private final String accountSid;
    private final List<String> accountSidSet; // if not-null we need the cdrs that belong to several accounts
    private final Date startTime;  // to initialize it pass string arguments with  yyyy-MM-dd format
    private final Date endTime;
    private final String transcriptionText;
    private final Integer limit;
    private final Integer offset;
    private final String instanceid;

    public TranscriptionFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime,
                                  String transcriptionText, Integer limit, Integer offset) throws ParseException {
        this(accountSid, accountSidSet, startTime,endTime, transcriptionText, limit,offset,null);
    }

    public TranscriptionFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime,
                                  String transcriptionText, Integer limit, Integer offset, String instanceId) throws ParseException {
        this.accountSid = accountSid;
        this.accountSidSet = accountSidSet;

        this.transcriptionText = transcriptionText;
        this.limit = limit;
        this.offset = offset;
        if (startTime != null) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            Date date = parser.parse(startTime);
            this.startTime = date;
        } else
            this.startTime = null;

        if (endTime != null) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            Date date = parser.parse(endTime);
            this.endTime = date;
        } else {
            this.endTime = null;
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            this.instanceid = instanceId;
        } else {
            this.instanceid = null;
        }
    }

    public String getSid() {
        return accountSid;
    }

    public List<String> getAccountSidSet() {
        return accountSidSet;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public String getTranscriptionText() {
        return transcriptionText;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public String getInstanceid() { return instanceid; }
}
