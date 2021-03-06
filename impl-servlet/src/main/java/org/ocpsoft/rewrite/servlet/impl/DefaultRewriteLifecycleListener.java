package org.ocpsoft.rewrite.servlet.impl;

import javax.servlet.ServletRequest;

import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.servlet.RewriteWrappedResponse;
import org.ocpsoft.rewrite.servlet.http.event.HttpServletRewrite;
import org.ocpsoft.rewrite.servlet.spi.RewriteLifecycleListener;

public class DefaultRewriteLifecycleListener implements RewriteLifecycleListener<HttpServletRewrite>
{
   private static final String REQUEST_NESTING_KEY = DefaultRewriteLifecycleListener.class + "_request_nesting";

   @Override
   public boolean handles(Rewrite payload)
   {
      return payload instanceof HttpServletRewrite;
   }

   @Override
   public int priority()
   {
      return Integer.MAX_VALUE;
   }

   @Override
   public void beforeInboundRewrite(HttpServletRewrite event)
   {
      incrementRequestNesting(event);
   }

   @Override
   public void afterInboundLifecycle(HttpServletRewrite event)
   {
      decrementRequestNesting(event);
      if (getRequestNesting(event.getRequest()) == 0)
         RewriteWrappedResponse.getCurrentInstance(event.getRequest()).flushBufferedContent();
   }

   private void decrementRequestNesting(HttpServletRewrite event)
   {
      if (getRequestNesting(event.getRequest()) > 0)
         event.getRequest().setAttribute(REQUEST_NESTING_KEY, getRequestNesting(event.getRequest()) - 1);
   }

   private void incrementRequestNesting(HttpServletRewrite event)
   {
      event.getRequest().setAttribute(REQUEST_NESTING_KEY, getRequestNesting(event.getRequest()) + 1);
   }

   public static int getRequestNesting(ServletRequest event)
   {
      Integer nesting = (Integer) event.getAttribute(REQUEST_NESTING_KEY);
      return nesting == null ? 0 : nesting;
   }

   @Override
   public void beforeInboundLifecycle(HttpServletRewrite event)
   {}

   @Override
   public void afterInboundRewrite(HttpServletRewrite event)
   {}

   @Override
   public void beforeOutboundRewrite(HttpServletRewrite event)
   {}

   @Override
   public void afterOutboundRewrite(HttpServletRewrite event)
   {}

}
