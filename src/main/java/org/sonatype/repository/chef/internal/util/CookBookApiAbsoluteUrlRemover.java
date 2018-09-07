/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2018-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.repository.chef.internal.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.ContentTypes;
import org.sonatype.nexus.repository.view.payloads.StreamPayload.InputStreamSupplier;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.http.client.utils.URIBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

@Named
@Singleton
public class CookBookApiAbsoluteUrlRemover
    extends ComponentSupport
{
  private ChefDataAccess chefDataAccess;

  @Inject
  public CookBookApiAbsoluteUrlRemover(final ChefDataAccess chefDataAccess) {
    this.chefDataAccess = checkNotNull(chefDataAccess);
  }

  public Content rewriteCookBookDetailJsonToRemoveAbsoluteUrls(final Content content) throws IOException, URISyntaxException {
    String filePath = UUID.randomUUID().toString();
    FileOutputStream file = new FileOutputStream(filePath);

    JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(content.openInputStream())));
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(file, "UTF-8"));

    CookbookDetailsJsonStreamer streamer = new CookbookDetailsJsonStreamer(reader, writer);

    streamer.parseJson();

    reader.close();
    writer.close();

    InputStream is = new FileInputStream(filePath);

    File tempFile = new File(filePath);

    long length = tempFile.length();

    tempFile.delete();

    return chefDataAccess.toContent(
        new FileInputStreamSupplier(is),
        length,
        ContentTypes.APPLICATION_JSON
    );
  }

  public Content rewriteCookbookListJsonToRemoveAbsoluteUrls(final Content content, final String urlTokenName) throws IOException, URISyntaxException {
    String filePath = UUID.randomUUID().toString();
    FileOutputStream file = new FileOutputStream(filePath);

    JsonReader reader = new JsonReader(new InputStreamReader(new BufferedInputStream(content.openInputStream())));
    JsonWriter writer = new JsonWriter(new OutputStreamWriter(file, "UTF-8"));

    CookbookListJsonStreamer streamer = new CookbookListJsonStreamer(reader, writer);

    streamer.parseJson();

    reader.close();
    writer.close();

    InputStream is = new FileInputStream(filePath);

    File tempFile = new File(filePath);

    long length = tempFile.length();

    tempFile.delete();

    return chefDataAccess.toContent(
        new FileInputStreamSupplier(is),
        length,
        ContentTypes.APPLICATION_JSON
    );
  }

  private class FileInputStreamSupplier
    implements InputStreamSupplier
  {
    private InputStream is;

    public FileInputStreamSupplier(final InputStream is) {
      this.is = is;
    }

    @Override
    public InputStream get() {
      return this.is;
    }
  }

  private class CookbookDetailsJsonStreamer
    extends JsonStreamer
  {
    public CookbookDetailsJsonStreamer(final JsonReader reader, final JsonWriter writer) {
      super(reader, writer);
    }

    @Override
    public void getAndSetName() throws IOException {
      String name = getReader().nextName();
      getWriter().name(name);
      JsonToken peek = getReader().peek();
      if (peek.equals(JsonToken.STRING) || (name.equals("versions") && peek.equals(JsonToken.BEGIN_ARRAY))) {
        maybeSetUrlToRelativeCase(getReader(), getWriter(), name);
      }
    }
  }

  private class CookbookListJsonStreamer
    extends JsonStreamer
  {
    public CookbookListJsonStreamer(final JsonReader reader, final JsonWriter writer) {
      super(reader, writer);
    }

    @Override
    public void getAndSetName() throws IOException {
      String name = getReader().nextName();
      getWriter().name(name);
      JsonToken peek = getReader().peek();
      if (peek.equals(JsonToken.STRING) && "cookbook".equals(name)) {
        maybeSetUrlToRelative(getReader(), getWriter());
      }
    }
  }

  private void maybeSetUrlToRelativeCase(final JsonReader reader, final JsonWriter writer, final String name)
      throws IOException
  {
    switch (name) {
      case "latest_version":
        doSetUrlAsRelative(reader, writer);
        break;
      case "versions":
        reader.beginArray();
        writer.beginArray();
        while (reader.hasNext()) {
          doSetUrlAsRelative(reader, writer);
        }
        reader.endArray();
        writer.endArray();
        break;
      default:
        writer.value(reader.nextString());
        break;
    }
  }

  private void doSetUrlAsRelative(final JsonReader reader, final JsonWriter writer)
      throws IOException
  {
    String url = reader.nextString();
    try {
      URI uri = new URIBuilder(url).build();
      if (uri.isAbsolute()) {
        String rightHand = uri.getPath();
        writer.value(rightHand);
      }
      else {
        writer.value(url);
      }
    } catch (URISyntaxException ex) {
      writer.value(url);
    }
  }

  private void maybeSetUrlToRelative(final JsonReader reader, final JsonWriter writer)
      throws IOException
  {
    doSetUrlAsRelative(reader, writer);
  }
}
