package eu.toop.tooppackagetracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet ("/debug-consume")
public class DebugConsumeServlet extends HttpServlet
{
  private static final Logger LOGGER = LoggerFactory.getLogger (DebugConsumeServlet.class);

  @Override
  protected void doGet (final HttpServletRequest aReq, final HttpServletResponse aResp) throws ServletException,
                                                                                        IOException
  {
    int nRepeats = -1;
    try
    {
      nRepeats = Integer.parseInt (aReq.getParameter ("repeats"));
    }
    catch (final Exception ex)
    {}
    if (nRepeats < 0)
      nRepeats = 10;

    int nTimeout = -1;
    try
    {
      nTimeout = Integer.parseInt (aReq.getParameter ("timeout"));
    }
    catch (final Exception ex)
    {}
    if (nTimeout < 0)
      nTimeout = 1000;

    LOGGER.info ("DebugConsumeServlet " + aReq.getRequestURL () + "?" + aReq.getQueryString ());

    final Map <String, Object> aProps = new LinkedHashMap <> ();
    aProps.put (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:7073");
    aProps.put (ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerManager.TOPIC_GROUP_ID);
    aProps.put (ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    aProps.put (ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    final StringBuilder aSB = new StringBuilder ();
    aSB.append ("<html><head><title>Kafka Debug</title></head><body>");

    {
      aSB.append ("<h1>Kafka Consumer Properties (" + aProps.size () + ")</h1><ul>");
      for (final Map.Entry <String, Object> aEntry : aProps.entrySet ())
        aSB.append ("<li><code>")
           .append (aEntry.getKey ())
           .append ("</code>=<code>")
           .append (aEntry.getValue ())
           .append ("</code></li>");
      aSB.append ("</ul>");
    }

    aSB.append ("<div>Opening Kafka consumer</div>");
    try (final KafkaConsumer <String, String> aConsumer = new KafkaConsumer <> (aProps,
                                                                                new StringDeserializer (),
                                                                                new StringDeserializer ()))
    {
      final Map <String, List <PartitionInfo>> topics = aConsumer.listTopics ();
      topics.remove ("__consumer_offsets");
      final List <String> aSortedTopics = new ArrayList <> (topics.size ());
      aSortedTopics.addAll (topics.keySet ());
      aSortedTopics.sort ( (o1, o2) -> o1.compareToIgnoreCase (o2));

      aSB.append ("<h1>All " + aSortedTopics.size () + " topics</h1><ul>");
      for (final String sTopic : aSortedTopics)
        aSB.append ("<li>").append (sTopic).append ("</li>");
      aSB.append ("</ul>");

      if (aSortedTopics.isEmpty ())
      {
        aSB.append ("<div>No topics - no consumption</div>");
      }
      else
      {
        aConsumer.subscribe (aSortedTopics);

        aSB.append ("<h1>Consumed records (" + nRepeats + " repeats; " + nTimeout + " ms)</h1>");
        for (int i = 0; i < nRepeats; ++i)
        {
          final ConsumerRecords <String, String> records = aConsumer.poll (nTimeout);
          for (final ConsumerRecord <String, String> record : records)
          {
            final String sRecord = "Consuming from topic = " +
                                   record.topic () +
                                   ", partition = " +
                                   record.partition () +
                                   ", offset = " +
                                   record.offset () +
                                   ", key = " +
                                   record.key () +
                                   ", value = " +
                                   record.value ();
            LOGGER.info (sRecord);
            aSB.append ("<div>" + sRecord + "</div>");
          }
        }
        aSB.append ("<div>Finished consuming records</div>");
      }
    }
    aSB.append ("<div>Closed Kafka consumer</div>");

    aSB.append ("</body></html>");

    LOGGER.info ("DebugConsumeServlet end");

    aResp.setHeader ("Content-Type", "text/html");
    aResp.getWriter ().println (aSB.toString ());
    aResp.getWriter ().close ();
  }
}
