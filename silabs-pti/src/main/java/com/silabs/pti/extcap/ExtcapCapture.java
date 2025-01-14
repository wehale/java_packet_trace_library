package com.silabs.pti.extcap;

import java.io.File;
import java.io.IOException;

import com.silabs.na.pcap.IPcapOutput;
import com.silabs.na.pcap.LinkType;
import com.silabs.na.pcap.Pcap;
import com.silabs.pti.adapter.AdapterPort;
import com.silabs.pti.adapter.AdapterSocketConnector;
import com.silabs.pti.adapter.DebugChannelFramer;
import com.silabs.pti.adapter.IConnection;
import com.silabs.pti.adapter.IConnectionListener;
import com.silabs.pti.adapter.IConnectivityLogger;
import com.silabs.pti.adapter.IFramer;
import com.silabs.pti.format.PcapngFormat;
import com.silabs.pti.log.PtiSeverity;

/**
 * Class that facilitates the capturing from WSTK into a pcap file.
 * 
 * @author timotej
 *
 */
public class ExtcapCapture implements IConnectivityLogger, IConnectionListener {

  private final String ifc, fifo, filter;
  private AdapterSocketConnector adapterConnector;
  private IPcapOutput output;
  private boolean isFinished = false;
  private int messageCount = 0;
  private IExtcapInterface ec;

  public ExtcapCapture(final String ifc, final String fifo, final String filter) {
    this.ifc = ifc;
    this.fifo = fifo;
    this.filter = filter;
  }

  public void capture(final IExtcapInterface ec) throws IOException {
    this.ec = ec;
    ec.log("capture: start capturing on adapter '" + ifc + "'");
    adapterConnector = new AdapterSocketConnector();
    output = Pcap.openForWriting(new File(fifo));
    output.writeInterfaceDescriptionBlock(LinkType.NETANALYZER, Pcap.RESOLUTION_MICROSECONDS);
    final IConnection c = adapterConnector.createConnection(ifc, AdapterPort.DEBUG.defaultPort(), this);
    final IFramer debugChannelFramer = new DebugChannelFramer(true);
    c.setFramers(debugChannelFramer, debugChannelFramer);
    c.connect();
    c.addConnectionListener(this);
    while (!isFinished) {
      try {
        Thread.sleep(200);
      } catch (final InterruptedException ie) {
        break;
      }
    }
    ec.log("capture: stop capturing on adapter '" + ifc + "'");
  }

  @Override
  public int bpsRecordPeriodMs() {
    return 5000;
  }

  @Override
  public void connectionStateChanged(final boolean isConnected) {
    if (!isConnected)
      isFinished = true;
  }

  @Override
  public int count() {
    return messageCount;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void log(final PtiSeverity severity, final String message, final Throwable throwable) {
    ec.log("capture " + severity.name().toLowerCase() + ": " + message
        + (throwable == null ? "" : (" [" + throwable.getMessage() + "]")));
  }

  @Override
  public void messageReceived(final byte[] message, final long pcTime) {
    messageCount++;
    try {
      PcapngFormat.writeRawUnframedDebugMessage(output, 0, pcTime, message);
    } catch (final IOException ioe) {
      if (!isFinished)
        ec.log("capture error: could not write PCAP file any more [" + ioe.getMessage() + "]");
      isFinished = true;
    }
  }
}
