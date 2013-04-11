/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.bot;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HTMLStreamWriter implements XMLStreamWriter {

    public HTMLStreamWriter(final Writer target) {
        this.target = target;
    }

    final Writer target;

    final Deque<Element> stack = new ArrayDeque<Element>();

    enum State {
        INITIAL,
        START_ATTRIBUTES,
        EMPTY_ATTRIBUTES,
    }

    private State state;

    private void toInitial() throws XMLStreamException {
        final State oldState = state;
        if (oldState == State.INITIAL) {
            return;
        }
        try {
            target.write('>');
            state = State.INITIAL;
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
        if (oldState == State.EMPTY_ATTRIBUTES) {
            if (stack.getFirst().getEnd() == Presence.REQUIRED) {
                writeEndElement();
            } else {
                stack.pop();
            }
        }
    }

    public void writeStartElement(final Element element) throws XMLStreamException {
        toInitial();
        try {
            target.write('<');
            target.write(element.name().toLowerCase());
            state = State.START_ATTRIBUTES;
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeStartElement(final String localName) throws XMLStreamException {
        writeStartElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeStartElement(final String namespaceURI, final String localName) throws XMLStreamException {
        writeStartElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeStartElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        writeStartElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeEmptyElement(final Element element) throws XMLStreamException {
        toInitial();
        try {
            target.write('<');
            target.write(element.name().toLowerCase());
            state = State.EMPTY_ATTRIBUTES;
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeEmptyElement(final String namespaceURI, final String localName) throws XMLStreamException {
        writeEmptyElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeEmptyElement(final String prefix, final String localName, final String namespaceURI) throws XMLStreamException {
        writeEmptyElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeEmptyElement(final String localName) throws XMLStreamException {
        writeEmptyElement(Element.valueOf(localName.toUpperCase()));
    }

    public void writeEndElement() throws XMLStreamException {
        toInitial();
        final Element element = stack.pop();
        if (element.getEnd() == Presence.REQUIRED) {
            try {
                target.write("</");
                target.write(element.name().toLowerCase());
                target.write('>');
            } catch (IOException e) {
                throw new XMLStreamException(e);
            }
        }
    }

    public void writeEndDocument() throws XMLStreamException {
    }

    public void close() throws XMLStreamException {
        try {
            target.close();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void flush() throws XMLStreamException {
        try {
            target.flush();
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeAttribute(final String localName, final String value) throws XMLStreamException {
        if (state == State.INITIAL) {
            throw new XMLStreamException("Invalid state");
        }
        try {
            target.write(' ');
            writeString(localName);
            target.write("=\"");
            writeString(value);
            target.write('\"');
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeAttribute(final String prefix, final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        writeAttribute(localName, value);
    }

    public void writeAttribute(final String namespaceURI, final String localName, final String value) throws XMLStreamException {
        writeAttribute(localName, value);
    }

    public void writeNamespace(final String prefix, final String namespaceURI) throws XMLStreamException {
        // no op
    }

    public void writeDefaultNamespace(final String namespaceURI) throws XMLStreamException {
        // no op
    }

    public void writeComment(final String data) throws XMLStreamException {
        // no op
    }

    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        // no op
    }

    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        // no op
    }

    public void writeCData(final String data) throws XMLStreamException {
        try {
            writeString(data);
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeDTD(final String dtd) throws XMLStreamException {
    }

    public void writeEntityRef(final String name) throws XMLStreamException {
    }

    public void writeStartDocument() throws XMLStreamException {
    }

    public void writeStartDocument(final String version) throws XMLStreamException {
    }

    public void writeStartDocument(final String encoding, final String version) throws XMLStreamException {
    }

    public void writeCharacters(final String text) throws XMLStreamException {
        try {
            writeString(text);
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        try {
            writeString(new String(text, start, len));
        } catch (IOException e) {
            throw new XMLStreamException(e);
        }
    }

    public String getPrefix(final String uri) throws XMLStreamException {
        return null;
    }

    public void setPrefix(final String prefix, final String uri) throws XMLStreamException {
    }

    public void setDefaultNamespace(final String uri) throws XMLStreamException {
    }

    public void setNamespaceContext(final NamespaceContext context) throws XMLStreamException {
    }

    public NamespaceContext getNamespaceContext() {
        return null;
    }

    public Object getProperty(final String name) throws IllegalArgumentException {
        return null;
    }

    private void writeString(final String s) throws IOException {
        final Writer target = this.target;
        final int len = s.length();
        int ch;
        for (int i = 0; i < len; i += Character.charCount(ch)) {
            ch = s.codePointAt(i);
            switch (ch) {
                case 160:
                    target.write("&#nbsp;");
                    break;
                case 161:
                    target.write("&#iexcl;");
                    break;
                case 162:
                    target.write("&#cent;");
                    break;
                case 163:
                    target.write("&#pound;");
                    break;
                case 164:
                    target.write("&#curren;");
                    break;
                case 165:
                    target.write("&#yen;");
                    break;
                case 166:
                    target.write("&#brvbar;");
                    break;
                case 167:
                    target.write("&#sect;");
                    break;
                case 168:
                    target.write("&#uml;");
                    break;
                case 169:
                    target.write("&#copy;");
                    break;
                case 170:
                    target.write("&#ordf;");
                    break;
                case 171:
                    target.write("&#laquo;");
                    break;
                case 172:
                    target.write("&#not;");
                    break;
                case 173:
                    target.write("&#shy;");
                    break;
                case 174:
                    target.write("&#reg;");
                    break;
                case 175:
                    target.write("&#macr;");
                    break;
                case 176:
                    target.write("&#deg;");
                    break;
                case 177:
                    target.write("&#plusmn;");
                    break;
                case 178:
                    target.write("&#sup2;");
                    break;
                case 179:
                    target.write("&#sup3;");
                    break;
                case 180:
                    target.write("&#acute;");
                    break;
                case 181:
                    target.write("&#micro;");
                    break;
                case 182:
                    target.write("&#para;");
                    break;
                case 183:
                    target.write("&#middot;");
                    break;
                case 184:
                    target.write("&#cedil;");
                    break;
                case 185:
                    target.write("&#sup1;");
                    break;
                case 186:
                    target.write("&#ordm;");
                    break;
                case 187:
                    target.write("&#raquo;");
                    break;
                case 188:
                    target.write("&#frac14;");
                    break;
                case 189:
                    target.write("&#frac12;");
                    break;
                case 190:
                    target.write("&#frac34;");
                    break;
                case 191:
                    target.write("&#iquest;");
                    break;
                case 192:
                    target.write("&#Agrave;");
                    break;
                case 193:
                    target.write("&#Aacute;");
                    break;
                case 194:
                    target.write("&#Acirc;");
                    break;
                case 195:
                    target.write("&#Atilde;");
                    break;
                case 196:
                    target.write("&#Auml;");
                    break;
                case 197:
                    target.write("&#Aring;");
                    break;
                case 198:
                    target.write("&#AElig;");
                    break;
                case 199:
                    target.write("&#Ccedil;");
                    break;
                case 200:
                    target.write("&#Egrave;");
                    break;
                case 201:
                    target.write("&#Eacute;");
                    break;
                case 202:
                    target.write("&#Ecirc;");
                    break;
                case 203:
                    target.write("&#Euml;");
                    break;
                case 204:
                    target.write("&#Igrave;");
                    break;
                case 205:
                    target.write("&#Iacute;");
                    break;
                case 206:
                    target.write("&#Icirc;");
                    break;
                case 207:
                    target.write("&#Iuml;");
                    break;
                case 208:
                    target.write("&#ETH;");
                    break;
                case 209:
                    target.write("&#Ntilde;");
                    break;
                case 210:
                    target.write("&#Ograve;");
                    break;
                case 211:
                    target.write("&#Oacute;");
                    break;
                case 212:
                    target.write("&#Ocirc;");
                    break;
                case 213:
                    target.write("&#Otilde;");
                    break;
                case 214:
                    target.write("&#Ouml;");
                    break;
                case 215:
                    target.write("&#times;");
                    break;
                case 216:
                    target.write("&#Oslash;");
                    break;
                case 217:
                    target.write("&#Ugrave;");
                    break;
                case 218:
                    target.write("&#Uacute;");
                    break;
                case 219:
                    target.write("&#Ucirc;");
                    break;
                case 220:
                    target.write("&#Uuml;");
                    break;
                case 221:
                    target.write("&#Yacute;");
                    break;
                case 222:
                    target.write("&#THORN;");
                    break;
                case 223:
                    target.write("&#szlig;");
                    break;
                case 224:
                    target.write("&#agrave;");
                    break;
                case 225:
                    target.write("&#aacute;");
                    break;
                case 226:
                    target.write("&#acirc;");
                    break;
                case 227:
                    target.write("&#atilde;");
                    break;
                case 228:
                    target.write("&#auml;");
                    break;
                case 229:
                    target.write("&#aring;");
                    break;
                case 230:
                    target.write("&#aelig;");
                    break;
                case 231:
                    target.write("&#ccedil;");
                    break;
                case 232:
                    target.write("&#egrave;");
                    break;
                case 233:
                    target.write("&#eacute;");
                    break;
                case 234:
                    target.write("&#ecirc;");
                    break;
                case 235:
                    target.write("&#euml;");
                    break;
                case 236:
                    target.write("&#igrave;");
                    break;
                case 237:
                    target.write("&#iacute;");
                    break;
                case 238:
                    target.write("&#icirc;");
                    break;
                case 239:
                    target.write("&#iuml;");
                    break;
                case 240:
                    target.write("&#eth;");
                    break;
                case 241:
                    target.write("&#ntilde;");
                    break;
                case 242:
                    target.write("&#ograve;");
                    break;
                case 243:
                    target.write("&#oacute;");
                    break;
                case 244:
                    target.write("&#ocirc;");
                    break;
                case 245:
                    target.write("&#otilde;");
                    break;
                case 246:
                    target.write("&#ouml;");
                    break;
                case 247:
                    target.write("&#divide;");
                    break;
                case 248:
                    target.write("&#oslash;");
                    break;
                case 249:
                    target.write("&#ugrave;");
                    break;
                case 250:
                    target.write("&#uacute;");
                    break;
                case 251:
                    target.write("&#ucirc;");
                    break;
                case 252:
                    target.write("&#uuml;");
                    break;
                case 253:
                    target.write("&#yacute;");
                    break;
                case 254:
                    target.write("&#thorn;");
                    break;
                case 255:
                    target.write("&#yuml;");
                    break;
                case 402:
                    target.write("&#fnof;");
                    break;

                case 913:
                    target.write("&#Alpha;");
                    break;
                case 914:
                    target.write("&#Beta;");
                    break;
                case 915:
                    target.write("&#Gamma;");
                    break;
                case 916:
                    target.write("&#Delta;");
                    break;
                case 917:
                    target.write("&#Epsilon;");
                    break;
                case 918:
                    target.write("&#Zeta;");
                    break;
                case 919:
                    target.write("&#Eta;");
                    break;
                case 920:
                    target.write("&#Theta;");
                    break;
                case 921:
                    target.write("&#Iota;");
                    break;
                case 922:
                    target.write("&#Kappa;");
                    break;
                case 923:
                    target.write("&#Lambda;");
                    break;
                case 924:
                    target.write("&#Mu;");
                    break;
                case 925:
                    target.write("&#Nu;");
                    break;
                case 926:
                    target.write("&#Xi;");
                    break;
                case 927:
                    target.write("&#Omicron;");
                    break;
                case 928:
                    target.write("&#Pi;");
                    break;
                case 929:
                    target.write("&#Rho;");
                    break;

                case 931:
                    target.write("&#Sigma;");
                    break;
                case 932:
                    target.write("&#Tau;");
                    break;
                case 933:
                    target.write("&#Upsilon;");
                    break;
                case 934:
                    target.write("&#Phi;");
                    break;
                case 935:
                    target.write("&#Chi;");
                    break;
                case 936:
                    target.write("&#Psi;");
                    break;
                case 937:
                    target.write("&#Omega;");
                    break;

                case 945:
                    target.write("&#alpha;");
                    break;
                case 946:
                    target.write("&#beta;");
                    break;
                case 947:
                    target.write("&#gamma;");
                    break;
                case 948:
                    target.write("&#delta;");
                    break;
                case 949:
                    target.write("&#epsilon;");
                    break;
                case 950:
                    target.write("&#zeta;");
                    break;
                case 951:
                    target.write("&#eta;");
                    break;
                case 952:
                    target.write("&#theta;");
                    break;
                case 953:
                    target.write("&#iota;");
                    break;
                case 954:
                    target.write("&#kappa;");
                    break;
                case 955:
                    target.write("&#lambda;");
                    break;
                case 956:
                    target.write("&#mu;");
                    break;
                case 957:
                    target.write("&#nu;");
                    break;
                case 958:
                    target.write("&#xi;");
                    break;
                case 959:
                    target.write("&#omicron;");
                    break;
                case 960:
                    target.write("&#pi;");
                    break;
                case 961:
                    target.write("&#rho;");
                    break;
                case 962:
                    target.write("&#sigmaf;");
                    break;
                case 963:
                    target.write("&#sigma;");
                    break;
                case 964:
                    target.write("&#tau;");
                    break;
                case 965:
                    target.write("&#upsilon;");
                    break;
                case 966:
                    target.write("&#phi;");
                    break;
                case 967:
                    target.write("&#chi;");
                    break;
                case 968:
                    target.write("&#psi;");
                    break;
                case 969:
                    target.write("&#omega;");
                    break;
                case 977:
                    target.write("&#thetasym;");
                    break;
                case 978:
                    target.write("&#upsih;");
                    break;
                case 982:
                    target.write("&#piv;");
                    break;

                case 8226:
                    target.write("&#bull;");
                    break;

                case 8230:
                    target.write("&#hellip;");
                    break;
                case 8242:
                    target.write("&#prime;");
                    break;
                case 8243:
                    target.write("&#Prime;");
                    break;
                case 8254:
                    target.write("&#oline;");
                    break;
                case 8260:
                    target.write("&#frasl;");
                    break;

                case 8472:
                    target.write("&#weierp;");
                    break;
                case 8465:
                    target.write("&#image;");
                    break;
                case 8476:
                    target.write("&#real;");
                    break;
                case 8482:
                    target.write("&#trade;");
                    break;
                case 8501:
                    target.write("&#alefsym;");
                    break;

                case 8592:
                    target.write("&#larr;");
                    break;
                case 8593:
                    target.write("&#uarr;");
                    break;
                case 8594:
                    target.write("&#rarr;");
                    break;
                case 8595:
                    target.write("&#darr;");
                    break;
                case 8596:
                    target.write("&#harr;");
                    break;
                case 8629:
                    target.write("&#crarr;");
                    break;
                case 8656:
                    target.write("&#lArr;");
                    break;

                case 8657:
                    target.write("&#uArr;");
                    break;
                case 8658:
                    target.write("&#rArr;");
                    break;

                case 8659:
                    target.write("&#dArr;");
                    break;
                case 8660:
                    target.write("&#hArr;");
                    break;

                case 8704:
                    target.write("&#forall;");
                    break;
                case 8706:
                    target.write("&#part;");
                    break;
                case 8707:
                    target.write("&#exist;");
                    break;
                case 8709:
                    target.write("&#empty;");
                    break;
                case 8711:
                    target.write("&#nabla;");
                    break;
                case 8712:
                    target.write("&#isin;");
                    break;
                case 8713:
                    target.write("&#notin;");
                    break;
                case 8715:
                    target.write("&#ni;");
                    break;

                case 8719:
                    target.write("&#prod;");
                    break;

                case 8721:
                    target.write("&#sum;");
                    break;

                case 8722:
                    target.write("&#minus;");
                    break;
                case 8727:
                    target.write("&#lowast;");
                    break;
                case 8730:
                    target.write("&#radic;");
                    break;
                case 8733:
                    target.write("&#prop;");
                    break;
                case 8734:
                    target.write("&#infin;");
                    break;
                case 8736:
                    target.write("&#ang;");
                    break;
                case 8743:
                    target.write("&#and;");
                    break;
                case 8744:
                    target.write("&#or;");
                    break;
                case 8745:
                    target.write("&#cap;");
                    break;
                case 8746:
                    target.write("&#cup;");
                    break;
                case 8747:
                    target.write("&#int;");
                    break;
                case 8756:
                    target.write("&#there4;");
                    break;
                case 8764:
                    target.write("&#sim;");
                    break;

                case 8773:
                    target.write("&#cong;");
                    break;
                case 8776:
                    target.write("&#asymp;");
                    break;
                case 8800:
                    target.write("&#ne;");
                    break;
                case 8801:
                    target.write("&#equiv;");
                    break;
                case 8804:
                    target.write("&#le;");
                    break;
                case 8805:
                    target.write("&#ge;");
                    break;
                case 8834:
                    target.write("&#sub;");
                    break;
                case 8835:
                    target.write("&#sup;");
                    break;

                case 8836:
                    target.write("&#nsub;");
                    break;
                case 8838:
                    target.write("&#sube;");
                    break;
                case 8839:
                    target.write("&#supe;");
                    break;
                case 8853:
                    target.write("&#oplus;");
                    break;
                case 8855:
                    target.write("&#otimes;");
                    break;
                case 8869:
                    target.write("&#perp;");
                    break;
                case 8901:
                    target.write("&#sdot;");
                    break;

                case 8968:
                    target.write("&#lceil;");
                    break;
                case 8969:
                    target.write("&#rceil;");
                    break;
                case 8970:
                    target.write("&#lfloor;");
                    break;
                case 8971:
                    target.write("&#rfloor;");
                    break;
                case 9001:
                    target.write("&#lang;");
                    break;

                case 9002:
                    target.write("&#rang;");
                    break;

                case 9674:
                    target.write("&#loz;");
                    break;

                case 9824:
                    target.write("&#spades;");
                    break;

                case 9827:
                    target.write("&#clubs;");
                    break;
                case 9829:
                    target.write("&#hearts;");
                    break;
                case 9830:
                    target.write("&#diams;");
                    break;
                case 34:
                    target.write("&#quot;");
                    break;
                case 38:
                    target.write("&#amp;");
                    break;
                case 60:
                    target.write("&#lt;");
                    break;
                case 62:
                    target.write("&#gt;");
                    break;

                case 338:
                    target.write("&#OElig;");
                    break;
                case 339:
                    target.write("&#oelig;");
                    break;

                case 352:
                    target.write("&#Scaron;");
                    break;
                case 353:
                    target.write("&#scaron;");
                    break;
                case 376:
                    target.write("&#Yuml;");
                    break;

                case 710:
                    target.write("&#circ;");
                    break;
                case 732:
                    target.write("&#tilde;");
                    break;

                case 8194:
                    target.write("&#ensp;");
                    break;
                case 8195:
                    target.write("&#emsp;");
                    break;
                case 8201:
                    target.write("&#thinsp;");
                    break;
                case 8204:
                    target.write("&#zwnj;");
                    break;
                case 8205:
                    target.write("&#zwj;");
                    break;
                case 8206:
                    target.write("&#lrm;");
                    break;
                case 8207:
                    target.write("&#rlm;");
                    break;
                case 8211:
                    target.write("&#ndash;");
                    break;
                case 8212:
                    target.write("&#mdash;");
                    break;
                case 8216:
                    target.write("&#lsquo;");
                    break;
                case 8217:
                    target.write("&#rsquo;");
                    break;
                case 8218:
                    target.write("&#sbquo;");
                    break;
                case 8220:
                    target.write("&#ldquo;");
                    break;

                default: {
                    if (Character.isWhitespace(ch)) {
                        target.write(ch);
                    } else if (Character.isISOControl(ch)) {
                        target.write("&#");
                        target.write(Integer.toString(ch));
                        target.write(';');
                    } else {
                        target.write(ch);
                    }
                }
            }
        }
    }

    public enum Presence {
        REQUIRED,
        OPTIONAL,
        FORBIDDEN,
        ;
    }

    public enum Element {
        A,
        ABBR,
        ACRONYM,
        ADDRESS,
        @Deprecated
        APPLET,
        AREA(true),
        B,
        BASE(true),
        @Deprecated
        BASEFONT(true),
        BDO,
        BIG,
        BLOCKQUOTE,
        BODY(Presence.OPTIONAL, Presence.OPTIONAL),
        BR(true),
        BUTTON,
        CAPTION,
        @Deprecated
        CENTER,
        CITE,
        CODE,
        COL(true),
        COLGROUP(Presence.REQUIRED, Presence.OPTIONAL),
        DD(Presence.REQUIRED, Presence.OPTIONAL),
        DEL,
        DFN,
        DIR,
        DIV,
        DL,
        DT(Presence.REQUIRED, Presence.OPTIONAL),
        EM,
        FIELDSET,
        @Deprecated
        FONT,
        FORM,
        FRAME(true),
        FRAMESET,
        H1,
        H2,
        H3,
        H4,
        H5,
        H6,
        HEAD(Presence.OPTIONAL, Presence.OPTIONAL),
        HR(true),
        HTML(Presence.OPTIONAL, Presence.OPTIONAL),
        I,
        IFRAME,
        IMG(true),
        INPUT(true),
        INS,
        @Deprecated
        ISINDEX(true),
        KBD,
        LABEL,
        LEGEND,
        LI(Presence.REQUIRED, Presence.OPTIONAL),
        LINK(true),
        MAP,
        @Deprecated
        MENU,
        META(true),
        NOFRAMES,
        NOSCRIPT,
        OBJECT,
        OL,
        OPTGROUP,
        OPTION(Presence.REQUIRED, Presence.OPTIONAL),
        P(Presence.REQUIRED, Presence.OPTIONAL),
        PARAM(true),
        PRE,
        Q,
        @Deprecated
        S,
        SAMP,
        SCRIPT,
        SELECT,
        SMALL,
        SPAN,
        @Deprecated
        STRIKE,
        STRONG,
        STYLE,
        SUB,
        SUP,
        TABLE,
        TBODY(Presence.OPTIONAL, Presence.OPTIONAL),
        TD(Presence.REQUIRED, Presence.OPTIONAL),
        TEXTAREA,
        TFOOT(Presence.REQUIRED, Presence.OPTIONAL),
        TH(Presence.REQUIRED, Presence.OPTIONAL),
        THEAD(Presence.REQUIRED, Presence.OPTIONAL),
        TITLE,
        TR(Presence.REQUIRED, Presence.OPTIONAL),
        TT,
        @Deprecated
        U,
        UL,
        VAR,
        ;

        private final boolean empty;
        private final Presence start;
        private final Presence end;

        Element() {
            this(false, Presence.REQUIRED, Presence.REQUIRED);
        }

        Element(final boolean empty) {
            this(empty, Presence.REQUIRED, Presence.FORBIDDEN);
        }

        Element(final Presence start, final Presence end) {
            this(false, start, end);
        }

        Element(boolean empty, Presence start, Presence end) {
            this.empty = empty;
            this.start = start;
            this.end = end;
        }

        public boolean isEmpty() {
            return empty;
        }

        public Presence getStart() {
            return start;
        }

        public Presence getEnd() {
            return end;
        }
    }

}
