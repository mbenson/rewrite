/*
 * Copyright 2012 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ocpsoft.rewrite.servlet.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ocpsoft.common.util.Streams;
import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.exception.RewriteException;
import org.ocpsoft.rewrite.servlet.RewriteLifecycleContext;
import org.ocpsoft.rewrite.servlet.RewriteWrappedResponse;
import org.ocpsoft.rewrite.servlet.config.response.ResponseContent;
import org.ocpsoft.rewrite.servlet.config.response.ResponseContentInterceptor;
import org.ocpsoft.rewrite.servlet.config.response.ResponseStreamWrapper;
import org.ocpsoft.rewrite.servlet.event.BaseRewrite.Flow;
import org.ocpsoft.rewrite.servlet.http.event.HttpOutboundServletRewrite;
import org.ocpsoft.rewrite.servlet.http.event.HttpServletRewrite;
import org.ocpsoft.rewrite.servlet.spi.RewriteLifecycleListener;
import org.ocpsoft.rewrite.spi.RewriteProvider;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class HttpRewriteWrappedResponse extends RewriteWrappedResponse
{
   private final HttpServletRequest request;

   public HttpRewriteWrappedResponse(final HttpServletRequest request, final HttpServletResponse response)
   {
      super(request, response);
      this.request = request;

      if (getCurrentInstance(request) == null) {
         super.setCurrentInstance(this);
      }
   }

   /*
    * Buffering Facilities
    */
   private ByteArrayOutputStream bufferedResponseContent = new ByteArrayOutputStream();
   private PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(bufferedResponseContent,
            Charset.forName(getCharacterEncoding())), true);
   private List<ResponseContentInterceptor> responseContentInterceptors = new ArrayList<ResponseContentInterceptor>();
   private List<ResponseStreamWrapper> responseStreamWrappers = new ArrayList<ResponseStreamWrapper>();

   private boolean contentWritten = false;
   private ServletOutputStream outputStream = null;

   @Override
   public boolean isResponseContentIntercepted()
   {
      return !responseContentInterceptors.isEmpty();
   }

   @Override
   public boolean isResponseStreamWrapped()
   {
      return !responseStreamWrappers.isEmpty();
   }

   @Override
   public void addContentInterceptor(ResponseContentInterceptor stage) throws IllegalStateException
   {
      if (areStreamsLocked())
      {
         throw new IllegalStateException(
                  "Cannot add output buffers to Response once request processing has been passed to the application.");
      }
      this.responseContentInterceptors.add(stage);
   }

   @Override
   public void addStreamWrapper(ResponseStreamWrapper wrapper)
   {
      if (areStreamsLocked())
      {
         throw new IllegalStateException(
                  "Cannot add output buffers to Response once request processing has been passed to the application.");
      }
      this.responseStreamWrappers.add(wrapper);
   }

   private boolean areStreamsLocked()
   {
      return contentWritten;
   }

   private void lockStreams()
   {
      this.contentWritten = true;
   }

   /**
    * Cause any buffered {@link ServletResponse} content to be processed and flushed to the client.
    */
   @Override
   public void flushBufferedContent()
   {
      if (isResponseContentIntercepted())
      {
         try {
            ResponseContent buffer = new ResponseContentImpl(bufferedResponseContent.toByteArray(),
                     Charset.forName(getCharacterEncoding()));
            new ResponseContentInterceptorChainImpl(responseContentInterceptors).begin(new HttpBufferRewriteImpl(
                     request, this), buffer);

            if (!Charset.forName(getCharacterEncoding()).equals(buffer.getCharset()))
               setCharacterEncoding(buffer.getCharset().name());

            Streams.copy(new ByteArrayInputStream(buffer.getContents()), super.getOutputStream());
            if (printWriter != null) {
               printWriter.close();
            }
            if (bufferedResponseContent != null) {
               bufferedResponseContent.close();
            }
         }
         catch (IOException e) {
            throw new RewriteException("Error occurred when flushing response content buffered by "
                     + responseContentInterceptors, e);
         }
      }
   }

   @Override
   public String toString()
   {
      if (isResponseContentIntercepted())
      {
         try {
            return bufferedResponseContent.toString(getCharacterEncoding());
         }
         catch (UnsupportedEncodingException e) {
            throw new RewriteException("Response accepted invalid character encoding " + getCharacterEncoding(), e);
         }
      }
      else
         return super.toString();
   }

   @Override
   public PrintWriter getWriter()
   {
      if (isResponseContentIntercepted())
         return printWriter;
      else
         try {
            lockStreams();
            return super.getWriter();
         }
         catch (IOException e) {
            throw new RewriteException("Could not get response writer.", e);
         }
   }

   @Override
   public ServletOutputStream getOutputStream()
   {
      if (outputStream == null)
      {
         if (isResponseContentIntercepted())
            outputStream = new RewriteServletOutputStream(bufferedResponseContent);
         else {
            try {
               lockStreams();
               outputStream = super.getOutputStream();
            }
            catch (IOException e) {
               throw new RewriteException("Could not get response output stream.", e);
            }
         }

         if (isResponseStreamWrapped())
         {
            HttpServletRewrite event = new HttpBufferRewriteImpl(request, this);
            OutputStream wrapped = outputStream;
            for (ResponseStreamWrapper wrapper : responseStreamWrappers) {
               wrapped = wrapper.wrap(event, wrapped);
            }
            outputStream = new RewriteServletOutputStream(wrapped);
         }
      }

      return outputStream;
   }

   @Override
   public void setContentLength(int contentLength)
   {
      lockStreams();
      /*
       * Prevent content-length being set as the page might be modified.
       */
      if (!isResponseContentIntercepted())
      {
         if (isResponseStreamWrapped())
         {
            setHeader("X-Uncompressed-Content-Length", String.valueOf(contentLength));
         }
         else
            super.setContentLength(contentLength);

      }
   }

   @Override
   public void flushBuffer() throws IOException
   {
      if (isResponseContentIntercepted())
         bufferedResponseContent.flush();
      else
      {
         lockStreams();
         super.flushBuffer();
      }
   }

   /**
    * Buffered {@link ServletOutputStream} implementation.
    * 
    * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
    */
   private class RewriteServletOutputStream extends ServletOutputStream
   {
      private OutputStream stream;

      public RewriteServletOutputStream(OutputStream outputStream)
      {
         this.stream = outputStream;
      }

      public void write(int b)
      {
         try {
            stream.write(b);
         }
         catch (IOException e) {
            throw new RewriteException("Error writing int to stream [" + stream + "]", e);
         }
      }

      public void write(byte[] bytes) throws IOException
      {
         stream.write(bytes);
      }

      public void write(byte[] bytes, int off, int len)
      {
         try {
            stream.write(bytes, off, len);
         }
         catch (IOException e) {
            throw new RewriteException("Error writing bytes to stream [" + stream + "] at offset [" + off
                     + "] with length [" + len + "]", e);
         }
      }
   }

   /*
    * End buffering facilities
    */
   @Override
   public String encodeRedirectUrl(final String url)
   {
      return encodeRedirectURL(url);
   }

   @Override
   public String encodeUrl(final String url)
   {
      return encodeURL(url);
   }

   @Override
   public String encodeRedirectURL(final String url)
   {
      HttpOutboundServletRewrite event = new HttpOutboundRewriteImpl(request, this, url);
      rewrite(event);

      if (event.getFlow().is(Flow.ABORT_REQUEST))
      {
         return event.getOutboundURL();
      }

      return super.encodeRedirectURL(event.getOutboundURL());
   }

   @Override
   public String encodeURL(final String url)
   {
      HttpOutboundServletRewrite event = new HttpOutboundRewriteImpl(request, this, url);
      rewrite(event);

      if (event.getFlow().is(Flow.ABORT_REQUEST))
      {
         return event.getOutboundURL();
      }

      return super.encodeURL(event.getOutboundURL());
   }

   private void rewrite(final HttpOutboundServletRewrite event)
   {
      @SuppressWarnings("unchecked")
      RewriteLifecycleContext<ServletContext> context = (RewriteLifecycleContext<ServletContext>) request
               .getAttribute(RewriteLifecycleContext.LIFECYCLE_CONTEXT_KEY);
      for (RewriteLifecycleListener<Rewrite> listener : context.getRewriteLifecycleListeners())
      {
         listener.beforeOutboundRewrite(event);
      }
      
      for (RewriteProvider<ServletContext, Rewrite> p : context.getRewriteProviders())
      {
         if (p.handles(event))
         {
            p.rewrite(event);
            if (event.getFlow().is(Flow.HANDLED))
            {
               break;
            }
         }
      }

      for (RewriteLifecycleListener<Rewrite> listener : context.getRewriteLifecycleListeners())
      {
         listener.afterOutboundRewrite(event);
      }
   }

   @Override
   public void sendError(int sc, String msg) throws IOException
   {
      lockStreams();
      super.sendError(sc, msg);
   }

   @Override
   public void sendError(int sc) throws IOException
   {
      lockStreams();
      super.sendError(sc);
   }

   @Override
   public void sendRedirect(String location) throws IOException
   {
      lockStreams();
      super.sendRedirect(location);
   }

   @Override
   public void reset()
   {
      bufferedResponseContent.reset();
      super.reset();
   }

   @Override
   public void resetBuffer()
   {
      bufferedResponseContent.reset();
      super.resetBuffer();
   }

}
