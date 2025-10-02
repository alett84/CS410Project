import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/*
  CS410 – Phase 1: Scanner
  Minimal C-like Dialect

  Authors: Kumail Abbas ,
  Reviewer: 

*/

public class Scanner {

    // TOKEN MODEL 
    enum TokenType {
        // keywords & identifiers
        KEYWORD, IDENTIFIER,
        // literals
        INT_LITERAL, FLOAT_LITERAL, CHAR_LITERAL,
        // operators & punctuation
        ASSIGN, EQ, LT, GT, LE, GE, NE, NOT,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        LPAREN, RPAREN, LBRACE, RBRACE, SEMICOLON, COMMA,
        // control
        EOF, ERROR
    }

    static class Token {
        final TokenType type;
        final String lexeme;
        final int line;
        final int col;
        Token(TokenType type, String lexeme, int line, int col) {
            this.type = type;
            this.lexeme = lexeme;
            this.line = line;
            this.col = col;
        }
        @Override public String toString() {
            switch (type) {
                case IDENTIFIER, KEYWORD, INT_LITERAL, FLOAT_LITERAL, CHAR_LITERAL:
                    return type + "\t" + lexeme;
                case EOF:
                    return "EOF";
                default:
                    return type.name();
            }
        }
    }

    // SCANNER ENGINE 
    public static class ScannerEngine {

        // Character classes (columns)
        static final int CC_LETTER = 0;
        static final int CC_DIGIT = 1;
        static final int CC_UNDERSCORE = 2;
        static final int CC_DOT = 3; // only used to advance from INT to FLOAT; no standalone DOT token
        static final int CC_WS = 4;  // space, tab, CR, LF
        static final int CC_EQ_BANG_LT_GT = 5; // = ! < >
        static final int CC_PLUS_MINUS_STAR_SLASH = 6; // + - * /
        static final int CC_LPAREN = 7; // (
        static final int CC_RPAREN = 8; // )
        static final int CC_SEMI = 9;   // ;
        static final int CC_LBRACE = 10; // {
        static final int CC_RBRACE = 11; // }
        static final int CC_COMMA  = 12; // ,
        static final int CC_HASH   = 13; // # (preprocessor)
        static final int CC_SQUOTE = 14; // '
        static final int CC_EOF = 15;
        static final int CC_OTHER = 16;
        static final int CLASS_COUNT = 17;

        // States (rows)
        static final int S_START   = 0;
        static final int S_ID      = 1;
        static final int S_INT     = 2;
        static final int S_FLOAT   = 3;   // digits '.' digits+
        static final int S_CHAR    = 4;   // 'x'
        static final int S_OP_EQ   = 5;   // '=' (maybe '==')
        static final int S_OP_REL  = 6;   // '<' or '>' (maybe <= >=)
        static final int S_OP_BANG = 7;   // '!' or '!='
        static final int S_OP_PMSS = 8;   // + - * / (single-char ops only)
        static final int S_LPAREN  = 9;
        static final int S_RPAREN  = 10;
        static final int S_SEMI    = 11;
        static final int S_LBRACE  = 12;
        static final int S_RBRACE  = 13;
        static final int S_COMMA   = 14;
        static final int S_ERROR   = 15;
        static final int S_EOF     = 16;
        static final int STATE_COUNT = 17;

        private final int[][] T = new int[STATE_COUNT][CLASS_COUNT];
        private final PushbackReader in;
        private int line = 1, col = 0;
        private boolean deliveredEOF = false;

        private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
                "include", "define",
                "char", "short", "int", "long", "float", "double",
                "for", "while", "if", "else"
        ));

        ScannerEngine(Reader reader) {
            this.in = new PushbackReader(new BufferedReader(reader), 4);
            buildTransitions();
        }

        private void buildTransitions() {
            for (int s = 0; s < STATE_COUNT; s++) Arrays.fill(T[s], -1);

            // START transitions
            T[S_START][CC_WS] = S_START; // skipped outside
            T[S_START][CC_LETTER] = S_ID;
            T[S_START][CC_UNDERSCORE] = S_ID;
            T[S_START][CC_DIGIT] = S_INT;
            T[S_START][CC_EQ_BANG_LT_GT] = S_OP_EQ; // refine by first char
            T[S_START][CC_PLUS_MINUS_STAR_SLASH] = S_OP_PMSS;
            T[S_START][CC_LPAREN] = S_LPAREN;
            T[S_START][CC_RPAREN] = S_RPAREN;
            T[S_START][CC_SEMI]   = S_SEMI;
            T[S_START][CC_LBRACE] = S_LBRACE;
            T[S_START][CC_RBRACE] = S_RBRACE;
            T[S_START][CC_COMMA]  = S_COMMA;
            T[S_START][CC_SQUOTE] = S_CHAR;  // char literal
            T[S_START][CC_HASH]   = S_START; // ignore '#'
            T[S_START][CC_EOF]    = S_EOF;
            T[S_START][CC_OTHER]  = S_ERROR;

            // ID
            T[S_ID][CC_LETTER] = S_ID;
            T[S_ID][CC_DIGIT] = S_ID;
            T[S_ID][CC_UNDERSCORE] = S_ID;

            // INT
            T[S_INT][CC_DIGIT] = S_INT;
            T[S_INT][CC_DOT]   = S_FLOAT; // must then see digits for valid float

            // FLOAT  (require at least one digit after dot; we only stay in S_FLOAT on more digits)
            T[S_FLOAT][CC_DIGIT] = S_FLOAT;

        }

        // Accepting states
        private boolean accepting(int state) {
            return switch (state) {
                case S_ID, S_INT, S_FLOAT, S_CHAR,
                     S_OP_EQ, S_OP_REL, S_OP_BANG, S_OP_PMSS,
                     S_LPAREN, S_RPAREN, S_SEMI, S_LBRACE, S_RBRACE, S_COMMA,
                     S_EOF, S_ERROR -> true;
                default -> false;
            };
        }

        private TokenType tokenTypeFor(int state, String lexeme) {
            switch (state) {
                case S_ID:
                    return KEYWORDS.contains(lexeme) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
                case S_INT:   return TokenType.INT_LITERAL;
                case S_FLOAT: return TokenType.FLOAT_LITERAL;
                case S_CHAR:  return TokenType.CHAR_LITERAL;

                case S_OP_PMSS:
                    switch (lexeme.charAt(0)) {
                        case '+': return TokenType.PLUS;
                        case '-': return TokenType.MINUS;
                        case '*': return TokenType.STAR;
                        case '/': return TokenType.SLASH;
                        default:  return TokenType.ERROR;
                    }

                case S_LPAREN: return TokenType.LPAREN;
                case S_RPAREN: return TokenType.RPAREN;
                case S_SEMI:   return TokenType.SEMICOLON;
                case S_LBRACE: return TokenType.LBRACE;
                case S_RBRACE: return TokenType.RBRACE;
                case S_COMMA:  return TokenType.COMMA;

                case S_OP_EQ:   // '=' or '=='
                    if (lexeme.length() == 2) return TokenType.EQ;
                    return TokenType.ASSIGN;
                case S_OP_REL:  // '<' '>' '<=' '>='
                    if (lexeme.length() == 2) return (lexeme.charAt(0) == '<') ? TokenType.LE : TokenType.GE;
                    return (lexeme.charAt(0) == '<') ? TokenType.LT : TokenType.GT;
                case S_OP_BANG: // '!' or '!='
                    return (lexeme.length() == 2) ? TokenType.NE : TokenType.NOT;

                case S_EOF:   return TokenType.EOF;
                case S_ERROR: return TokenType.ERROR;
                default:      return TokenType.ERROR;
            }
        }

        // I/O helpers
        private int read() throws IOException {
            int ch = in.read();
            if (ch == -1) { deliveredEOF = true; return -1; }
            if (ch == '\n') { line++; col = 0; } else { col++; }
            return ch;
        }
        private void unread(int ch) throws IOException {
            if (ch == -1) return;
            in.unread(ch);
            if (ch == '\n') { line--; /* best effort */ }
            else { col = Math.max(0, col - 1); }
        }

        private boolean isWS(int ch) { return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n'; }

        // Skip whitespace and comments; returns first non-ws char (already read) or -1
        private int skipSpaceAndComments() throws IOException {
            while (true) {
                int ch = read();
                if (ch == -1) return -1;

                // whitespace
                if (isWS(ch)) continue;

                // preprocessor hash -> ignore the hash only
                if (ch == '#') continue;

                // comments
                if (ch == '/') {
                    int la = read();
                    if (la == '/') {
                        // line comment
                        while (true) {
                            int c = read();
                            if (c == -1 || c == '\n') break;
                        }
                        continue; // restart outer loop
                    } else if (la == '*') {
                        // block comment
                        int prev = 0, cur;
                        while ((cur = read()) != -1) {
                            if (prev == '*' && cur == '/') break;
                            prev = cur;
                        }
                        continue;
                    } else {
                        // not a comment; push back lookahead and treat '/' as token
                        unread(la);
                        return '/';
                    }
                }

                // found a real char
                return ch;
            }
        }

        private int classify(int ch) {
            if (ch == -1) return CC_EOF;
            char c = (char) ch;
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') return CC_WS;
            if (c == '_') return CC_UNDERSCORE;
            if (Character.isLetter(c)) return CC_LETTER;
            if (Character.isDigit(c)) return CC_DIGIT;
            if (c == '.') return CC_DOT;
            if (c == '=' || c == '!' || c == '<' || c == '>') return CC_EQ_BANG_LT_GT;
            if (c == '+' || c == '-' || c == '*' || c == '/') return CC_PLUS_MINUS_STAR_SLASH;
            if (c == '(') return CC_LPAREN;
            if (c == ')') return CC_RPAREN;
            if (c == ';') return CC_SEMI;
            if (c == '{') return CC_LBRACE;
            if (c == '}') return CC_RBRACE;
            if (c == ',') return CC_COMMA;
            if (c == '#') return CC_HASH;
            if (c == '\'') return CC_SQUOTE;
            return CC_OTHER;
        }

        //  Core scanning 
        Token nextToken() throws IOException {
            if (deliveredEOF) return new Token(TokenType.EOF, "", line, col);

            int first = skipSpaceAndComments();
            int chClass = classify(first);

            int tokStartLine = line;
            int tokStartCol = Math.max(1, col); // first is already consumed by skipSpaceAndComments/read()

            if (chClass == CC_EOF) return new Token(TokenType.EOF, "", tokStartLine, tokStartCol);

            // Char literal: 'x'
            if (first == '\'') {
                StringBuilder sb = new StringBuilder();
                sb.append('\'');
                int c = read();
                if (c == -1 || c == '\n' || c == '\r' || c == '\'') {
                    // invalid char literal
                    if (c != -1) unread(c);
                    return new Token(TokenType.ERROR, sb.toString(), tokStartLine, tokStartCol);
                }
                sb.append((char)c);
                int close = read();
                if (close != '\'') {
                    if (close != -1) unread(close);
                    return new Token(TokenType.ERROR, sb.toString(), tokStartLine, tokStartCol);
                }
                sb.append('\'');
                return new Token(TokenType.CHAR_LITERAL, sb.toString(), tokStartLine, tokStartCol);
            }

            // Resolve first state
            int state = T[S_START][chClass];
            if (state == -1) state = S_ERROR;

            StringBuilder lexeme = new StringBuilder();
            lexeme.append((char) first);

            // Specialize operator families
            if (state == S_OP_EQ || state == S_OP_BANG || state == S_OP_REL) {
                char c0 = (char) first;
                if (c0 == '<' || c0 == '>') state = S_OP_REL;
                else if (c0 == '!') state = S_OP_BANG;
                else state = S_OP_EQ; // '='
            }

            // Main consume loop
            while (true) {
                // handle possible two-char ops with optional '='
                if (state == S_OP_EQ || state == S_OP_REL || state == S_OP_BANG) {
                    int la = read();
                    if (la == '=') {
                        lexeme.append('=');
                    } else {
                        unread(la);
                    }
                    break;
                }

                int la = read();
                int laClass = classify(la);

                // FLOAT rule enforcement: once we saw '.', we must see at least one digit (we only set S_FLOAT from INT on '.')
                if (state == S_INT && laClass == CC_DOT) {
                    // move to S_FLOAT but require next to be digit
                    int la2 = read();
                    if (Character.isDigit((char)la2)) {
                        lexeme.append('.');
                        lexeme.append((char)la2);
                        state = S_FLOAT;
                        continue; // keep consuming digits in S_FLOAT
                    } else {
                        // invalid float like "123." -> put back la2 and la ('.'), accept INT
                        if (la2 != -1) unread(la2);
                        unread(la);
                        break;
                    }
                }

                int ns = (la == -1) ? -1 : T[state][laClass];

                if (ns == -1 || laClass == CC_WS || laClass == CC_EOF || laClass == CC_HASH) {
                    if (la != -1 && laClass != CC_WS && laClass != CC_EOF) unread(la);
                    break;
                } else {
                    state = ns;
                    lexeme.append((char) la);
                }
            }

            TokenType type = tokenTypeFor(state, lexeme.toString());
            if (type == TokenType.ERROR) {
                return new Token(TokenType.ERROR, lexeme.toString(), tokStartLine, tokStartCol);
            }
            return new Token(type, lexeme.toString(), tokStartLine, tokStartCol);
        }

        List<Token> scanAll() throws IOException {
            List<Token> out = new ArrayList<>();
            while (true) {
                Token t = nextToken();
                out.add(t);
                if (t.type == TokenType.EOF) break;
            }
            return out;
        }
    }

    //  MAIN 
    public static void main(String[] args) throws Exception {
        Reader reader;
        if (args.length > 0) {
            reader = new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8);
        } else {
            reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        }
        ScannerEngine engine = new ScannerEngine(reader);
        List<Token> tokens = engine.scanAll();
        for (Token t : tokens) {
            System.out.println(t.toString());
        }
    }
}
