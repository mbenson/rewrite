/*
 * Copyright 2011 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
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
package org.ocpsoft.rewrite.servlet.config;

import org.ocpsoft.rewrite.bind.Bindable;
import org.ocpsoft.rewrite.bind.Binding;
import org.ocpsoft.rewrite.config.Operation;
import org.ocpsoft.rewrite.config.OperationBuilder;
import org.ocpsoft.rewrite.context.EvaluationContext;
import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.param.Parameter;
import org.ocpsoft.rewrite.param.ParameterBuilder;
import org.ocpsoft.rewrite.param.Parameterized;
import org.ocpsoft.rewrite.param.RegexParameterizedPattern;
import org.ocpsoft.rewrite.param.PatternParameter;
import org.ocpsoft.rewrite.servlet.config.IRedirect.RedirectParameter;

/**
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 */
public interface IRedirect extends Parameterized<IRedirect, RedirectParameter, String>, OperationBuilder
{
   public RegexParameterizedPattern getTargetExpression();

   public interface IRedirectParameter extends IRedirect, Bindable<RedirectParameter>, Parameter<RedirectParameter, String>
   {
      IRedirectParameter matches(String string);
   }

   public class RedirectParameter extends ParameterBuilder<RedirectParameter, String> implements IRedirectParameter
   {
      private final IRedirect parent;
      private final PatternParameter parameter;

      public RedirectParameter(IRedirect path, PatternParameter capture)
      {
         super(capture);
         this.parent = path;
         this.parameter = capture;
      }

      @Override
      public IRedirectParameter matches(String string)
      {
         parameter.matches(string);
         return this;
      }

      @Override
      public String getName()
      {
         return parameter.getName();
      }

      @Override
      public RedirectParameter where(String param)
      {
         return parent.where(param);
      }

      @Override
      public RedirectParameter where(String param, Binding binding)
      {
         return parent.where(param, binding);
      }

      @Override
      public void perform(Rewrite event, EvaluationContext context)
      {
         parent.perform(event, context);
      }

      @Override
      public RegexParameterizedPattern getTargetExpression()
      {
         return parent.getTargetExpression();
      }

      @Override
      public OperationBuilder and(Operation other)
      {
         return parent.and(other);
      }
   }
}
