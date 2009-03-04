/*
 * Copyright (c) 2007 World Wide Web Consortium,
 *
 * (Massachusetts Institute of Technology, European Research Consortium for
 * Informatics and Mathematics, Keio University). All Rights Reserved. This
 * work is distributed under the W3C(r) Software License [1] in the hope that
 * it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * [1] http://www.w3.org/Consortium/Legal/2002/copyright-software-20021231
 *
 * Difference to the original copy of this file:
 *   1) REMOVE public String getNamespaceURI();
 *   2) REMOVE public void stopImmediatePropagation();
 *   3) REMOVE public boolean getDefaultPrevented();
 *   4) REMOVE public void initEventNS(String namespaceURIArg,
 *                          String eventTypeArg,
 *                          boolean canBubbleArg,
 *                          boolean cancelableArg);
 *   5) ADD    public void initEvent(String eventTypeArg,
 *                          boolean canBubbleArg,
 *                          boolean cancelableArg,
 *                          int seekTo);
 *
 *   6) ADD    public int getSeekTo();
 */

package org.w3c.dom.events;

/**
 *  The <code>Event</code> interface is used to provide contextual information
 * about an event to the listener processing the event. An object which
 * implements the <code>Event</code> interface is passed as the parameter to
 * an <code>EventListener</code>. The object passed to the event listener
 * may also implement derived interfaces that provide access to information
 * directly relating to the type of event they represent.
 * <p> To create an instance of the <code>Event</code> interface, use the
 * <code>DocumentEvent.createEvent("Event")</code> method call.
 * <p>See also the <a href='http://www.w3.org/TR/2007/WD-DOM-Level-3-Events-20071207'>
   Document Object Model (DOM) Level 3 Events Specification
  </a>.
 * @since DOM Level 2
 */
public interface Event {
    // PhaseType
    /**
     *  The current event phase is the capture phase.
     */
    public static final short CAPTURING_PHASE           = 1;
    /**
     *  The current event is in the target phase, i.e. it is being evaluated
     * at the event target.
     */
    public static final short AT_TARGET                 = 2;
    /**
     *  The current event phase is the bubbling phase.
     */
    public static final short BUBBLING_PHASE            = 3;

    /**
     *  The local name of the event type. The name must be an <a href='http://www.w3.org/TR/2004/REC-xml-names11-20040204/#NT-NCName'>NCName</a> as defined in [<a href='http://www.w3.org/TR/2006/REC-xml-names11-20060816'>XML Namespaces 1.1</a>]
     *  and is case-sensitive.
     */
    public String getType();

    /**
     *  Used to indicate the event target. This attribute contains the target
     * node when used with the .
     */
    public EventTarget getTarget();

    /**
     *  Used to indicate the <code>EventTarget</code> whose
     * <code>EventListeners</code> are currently being processed. This is
     * particularly useful during the capture and bubbling phases. This
     * attribute could contain the target node or a target ancestor when
     * used with the .
     */
    public EventTarget getCurrentTarget();

    /**
     *  Used to indicate which phase of event flow is currently being
     * accomplished.
     */
    public short getEventPhase();

    /**
     *  Used to indicate whether or not an event is a bubbling event. If the
     * event can bubble the value is <code>true</code>, otherwise the value
     * is <code>false</code>.
     */
    public boolean getBubbles();

    /**
     *  Used to indicate whether or not an event can have its default action
     * prevented (see also ). If the default action can be prevented the
     * value is <code>true</code>, otherwise the value is <code>false</code>
     * .
     */
    public boolean getCancelable();

    /**
     *  Used to specify the time at which the event was created in
     * milliseconds relative to 1970-01-01T00:00:00Z. Due to the fact that
     * some systems may not provide this information the value of
     * <code>timeStamp</code> may be not available for all events. When not
     * available, the value is <code>0</code>.
     */
    public long getTimeStamp();

    /**
     *  Prevents other event listeners from being triggered but its effect is
     * deferred until all event listeners attached on the
     * <code>Event.currentTarget</code> have been triggered  . Once it has
     * been called, further calls to this method have no additional effect.
     * <p ><b>Note:</b>  This method does not prevent the default action from
     * being invoked; use <code>Event.preventDefault()</code> for that
     * effect.
     */
    public void stopPropagation();

    /**
     *  Signifies that the event is to be canceled, meaning any default action
     * normally taken by the implementation as a result of the event will
     * not occur (see also ). Calling this method for a non-cancelable event
     * has no effect.
     * <p ><b>Note:</b>  This method does not stop the event propagation; use
     * <code>Event.stopPropagation()</code> or
     * <code>Event.stopImmediatePropagation()</code> for that effect.
     */
    public void preventDefault();

    /**
     *  Initializes attributes of an <code>Event</code> created through the
     * <code>DocumentEvent.createEvent</code> method. This method may only
     * be called before the <code>Event</code> has been dispatched via the
     * <code>EventTarget.dispatchEvent()</code> method. If the method is
     * called several times before invoking
     * <code>EventTarget.dispatchEvent</code>, only the final invocation
     * takes precedence. This method has no effect if called after the event
     * has been dispatched. If called from a subclass of the
     * <code>Event</code> interface only the values specified in this method
     * are modified, all other attributes are left unchanged.
     * <br> This method sets the <code>Event.type</code> attribute to
     * <code>eventTypeArg</code>, and <code>Event.namespaceURI</code> to
     * <code>null</code>. To initialize an event with a namespace URI, use
     * the <code>Event.initEventNS()</code> method.
     * @param eventTypeArg  Specifies <code>Event.type</code>, the local name
     *   of the event type.
     * @param canBubbleArg  Specifies <code>Event.bubbles</code>. This
     *   parameter overrides the intrinsic bubbling behavior of the event.
     * @param cancelableArg  Specifies <code>Event.cancelable</code>. This
     *   parameter overrides the intrinsic cancelable behavior of the event.
     *
     */
    public void initEvent(String eventTypeArg,
                          boolean canBubbleArg,
                          boolean cancelableArg);

    /**
     *  Initializes attributes of an <code>Event</code> created through the
     * <code>DocumentEvent.createEvent</code> method. This method may only
     * be called before the <code>Event</code> has been dispatched via the
     * <code>EventTarget.dispatchEvent()</code> method. If the method is
     * called several times before invoking
     * <code>EventTarget.dispatchEvent</code>, only the final invocation
     * takes precedence. This method has no effect if called after the event
     * has been dispatched. If called from a subclass of the
     * <code>Event</code> interface only the values specified in this method
     * are modified, all other attributes are left unchanged.
     * <br> This method sets the <code>Event.type</code> attribute to
     * <code>eventTypeArg</code>, and <code>Event.namespaceURI</code> to
     * <code>null</code>. To initialize an event with a namespace URI, use
     * the <code>Event.initEventNS()</code> method.
     * @param eventTypeArg  Specifies <code>Event.type</code>, the local name
     *   of the event type.
     * @param canBubbleArg  Specifies <code>Event.bubbles</code>. This
     *   parameter overrides the intrinsic bubbling behavior of the event.
     * @param cancelableArg  Specifies <code>Event.cancelable</code>. This
     *   parameter overrides the intrinsic cancelable behavior of the event.
     * @param seekTo  Specifies <code>Event.seekTo</code>. the int seekTo of event.
     *
     */
    public void initEvent(String eventTypeArg,
                          boolean canBubbleArg,
                          boolean cancelableArg,
                          int seekTo);

    public int getSeekTo();
}
