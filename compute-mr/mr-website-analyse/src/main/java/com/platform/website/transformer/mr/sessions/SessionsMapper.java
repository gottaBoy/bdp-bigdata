package com.platform.website.transformer.mr.sessions;

import com.platform.website.common.DateEnum;
import com.platform.website.common.EventLogConstants;
import com.platform.website.common.GlobalConstants;
import com.platform.website.common.KpiType;
import com.platform.website.transformer.model.dim.base.BrowserDimension;
import com.platform.website.transformer.model.dim.base.DateDimension;
import com.platform.website.transformer.model.dim.base.KpiDimension;
import com.platform.website.transformer.model.dim.base.PlatformDimension;
import com.platform.website.transformer.model.dim.StatsCommonDimension;
import com.platform.website.transformer.model.dim.StatsUserDimension;
import com.platform.website.transformer.model.value.map.TimeOutputValue;
import com.platform.website.util.JdbcManager;
import com.platform.website.util.MemberUtil;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

public class SessionsMapper extends TableMapper<StatsUserDimension, TimeOutputValue> {

  private static final Logger logger = Logger.getLogger(
      SessionsMapper.class);
  private byte[] family = Bytes.toBytes(EventLogConstants.EVENT_LOGS_FAMILY_NAME);
  private StatsUserDimension outputKey = new StatsUserDimension();
  private TimeOutputValue outputValue = new TimeOutputValue();
  private BrowserDimension defaultBrowser = new BrowserDimension("", "");

  private KpiDimension sessionsKpi = new KpiDimension(KpiType.SESSIONS.name);
  private KpiDimension sessionsOfBrowserKpi = new KpiDimension(
      KpiType.BROWSER_SESSIONS.name);

  private Connection connection = null;


  @Override
  protected void setup( Context context) throws IOException, InterruptedException {
    super.setup(context);
    //?????????????????????
    Configuration conf = context.getConfiguration();
    try{
      this.connection = JdbcManager.getConnection(conf, GlobalConstants.WAREHOUSE_OF_WEBSITE);
      //???????????????????????????
      MemberUtil.deleteMemberInfoByDate(conf.get(GlobalConstants.RUNNING_DATE_PARAMS), this.connection);
    } catch (SQLException e) {
      logger.error("?????????????????????????????????", e);
      throw new IOException("???????????????????????????????????????", e);
    }
  }

  @Override
  protected void map(ImmutableBytesWritable key, Result value, Context context)
      throws IOException, InterruptedException {
    String sessionId = Bytes
        .toString(
            value.getValue(family, Bytes.toBytes(EventLogConstants.LOG_COLUMN_NAME_MEMBER_ID)));
    String platform = Bytes.toString(
        value.getValue(family, Bytes.toBytes(EventLogConstants.LOG_COLUMN_NAME_PLATFORM)));
    String serverTime = Bytes.toString(
        value.getValue(family, Bytes.toBytes(EventLogConstants.LOG_COLUMN_NAME_SERVER_TIME)));
    if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(serverTime) || StringUtils
        .isBlank(platform) || !StringUtils.isNumeric(serverTime.trim())) {
      System.out.println(Bytes.toString(value.getRow()));
      logger.warn("sessionId&servertime&platform?????????????????????serverTime??????????????????");
      return;
    }
    long longOfTime = Long.valueOf(serverTime.trim());
    if (longOfTime == -1) {
      //?????????s_time??????
      longOfTime = new Date().getTime();
    }
    DateDimension dateDimension = DateDimension.buildDate(longOfTime, DateEnum.DAY);
    List<PlatformDimension> platformDimensions = PlatformDimension.buildList(platform);
    //???browser???????????????
    String browserName = Bytes.toString(
        value.getValue(family, Bytes.toBytes(EventLogConstants.LOG_COLUMN_NAME_BROWSER_NAME)));
    String browserVersion = Bytes.toString(
        value.getValue(family, Bytes.toBytes(EventLogConstants.LOG_COLUMN_NAME_BROWSER_VERSION)));
    List<BrowserDimension> browserDimensions = BrowserDimension
        .buildList(browserName, browserVersion);

    //??????????????????
    outputValue.setId(sessionId);
    outputValue.setTime(longOfTime);
    StatsCommonDimension statsCommonDimension = this.outputKey.getStatsCommon();
    statsCommonDimension.setDate(dateDimension);

    for (PlatformDimension pf : platformDimensions) {
      //??????BrowserDimenson?????????
      outputKey.setBrowser(defaultBrowser);
      //??????platform dimension
      statsCommonDimension.setPlatform(pf);
      //??????kpi dimension
      statsCommonDimension.setKpi(sessionsKpi);
      context.write(this.outputKey, this.outputValue);

      //browser??????
      statsCommonDimension.setKpi(this.sessionsOfBrowserKpi);
      for (BrowserDimension br : browserDimensions) {
        this.outputKey.setBrowser(br);
        context.write(this.outputKey, this.outputValue);
      }
    }

  }

}
