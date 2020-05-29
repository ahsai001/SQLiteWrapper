package com.ahsailabs.sqlwsampleproject;

import com.ahsailabs.sqlitewrapper.SQLiteWrapper;
import com.ahsailabs.sqlwannotation.Check;
import com.ahsailabs.sqlwannotation.Column;
import com.ahsailabs.sqlwannotation.ForeignKey;
import com.ahsailabs.sqlwannotation.Index;
import com.ahsailabs.sqlwannotation.Table;
import com.ahsailabs.sqlwannotation.Unique;

import java.util.Date;

/**
 * Created by ahmad s on 2020-05-25.
 */


@Table(name="Response", recordLog=false, softDelete=true)
@Index(first = "status,time, oke,deh,mantab")
@Unique(first="status,time")
@Check(conditionalLogic="sdfasf")
public class Response extends SQLiteWrapper.TableClass {
    @Column(name="status", index=true, unique=true, notNull=true)
    public String status;

    @Column
    public int time;

    @Column(name="tanggal")
    public Date date;

    @Column
    @ForeignKey(parentTableName = "Request", parentColumnName = "yes_time")
    public float yesTime;
}
