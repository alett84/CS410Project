import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

//CS410 – Phase 2: Parser
//Minimal C-like Dialect
//Authors: Aidan Lett
//Reviewer: 


public class Parser {

    
    //Token model & source
    enum TokenType 
    {
        // identifiers/keywords
        IDENTIFIER, KEYWORD,

        // literals
        INT_LITERAL, FLOAT_LITERAL,

        // operators & punctuation
        ASSIGN,    
        EQ, NE, LT, GT, LE, GE, 
        PLUS, MINUS, STAR, SLASH,
        LPAREN, RPAREN, SEMICOLON,

        // special
        EOF, ERROR
    }


    static class Token 
    {
        final TokenType type;
        final String lexeme; 
        final int line, col;

        Token(TokenType type, String lexeme, int line, int col) 
        {
            this.type = type; 
            this.lexeme = lexeme; 
            this.line = line; 
            this.col = col;
        }

        @Override public String toString() 
        {
            return type + (switch (type) 
            {
                case IDENTIFIER, KEYWORD, INT_LITERAL, FLOAT_LITERAL -> "("+lexeme+")";
                default -> "";
            }) + " @" + line + ":" + col;

        }
    }


    interface TokenSource 
    {
        Token peek() throws IOException;
        Token next() throws IOException;
    }

    
    
static class ScannerAdapter implements TokenSource 
{
    private final Scanner.ScannerEngine eng;     //scanner from part 1 
    private Scanner.Token buffered;               // buffer 1-token lookahead

    ScannerAdapter(Reader r) 
    {
        this.eng = new Scanner.ScannerEngine(r);
    }


    @Override public Token peek() throws IOException 
    {
        if (buffered == null) buffered = eng.nextToken();
        return convert(buffered);
    }


    @Override public Token next() throws IOException 
    {
        Token t = peek();
        buffered = null;
        return t;
    }


    // Scanner.Token -> Parser.Token
    private Token convert(Scanner.Token st) 
    {
        TokenType tt = mapType(st.type, st.lexeme);
        String lex = mapLexeme(st.type, st.lexeme);
        return new Token(tt, lex, st.line, st.col);
    }


    // TokenType mapping between scanner and parser
    private TokenType mapType(Scanner.TokenType t, String lexeme) 
    {
        switch (t) 
        {
            // identifiers/keywords
            case IDENTIFIER:   return TokenType.IDENTIFIER;
            case KEYWORD:      return TokenType.KEYWORD;

            // literals
            case INT_LITERAL:  return TokenType.INT_LITERAL;
            case FLOAT_LITERAL:return TokenType.FLOAT_LITERAL;
            case CHAR_LITERAL: return TokenType.INT_LITERAL; 

            // operators / punctuation 
            case ASSIGN: return TokenType.ASSIGN;
            case EQ:     return TokenType.EQ;
            case NE:     return TokenType.NE;
            case LT:     return TokenType.LT;
            case GT:     return TokenType.GT;
            case LE:     return TokenType.LE;
            case GE:     return TokenType.GE;
            case PLUS:   return TokenType.PLUS;
            case MINUS:  return TokenType.MINUS;
            case STAR:   return TokenType.STAR;
            case SLASH:  return TokenType.SLASH;
            case LPAREN: return TokenType.LPAREN;
            case RPAREN: return TokenType.RPAREN;
            case SEMICOLON: return TokenType.SEMICOLON;

            // stuff parser doesn’t currently handle treated as ERROR so the parser will complain clearly
            case NOT:
            case PERCENT:
            case LBRACE:
            case RBRACE:
            case COMMA:
                return TokenType.ERROR;

            case EOF:    return TokenType.EOF;
            case ERROR:  return TokenType.ERROR;
        }
        return TokenType.ERROR;
    }


    // Convert char literals to an integer lexeme so expressions can use them
    private String mapLexeme(Scanner.TokenType t, String lexeme) 
    {
        if (t == Scanner.TokenType.CHAR_LITERAL) 
        {
            // Expect forms like: 'x'
            if (lexeme != null && lexeme.length() >= 3 && lexeme.charAt(0)=='\'' && lexeme.charAt(lexeme.length()-1)=='\'') 
            {
                char c;
                if (lexeme.length() == 3) 
                {
                    c = lexeme.charAt(1);
                } 
                else 
                {
                    // escape support like '\n' or '\'
                    char esc = lexeme.charAt(2);
                    switch (esc) 
                    {
                        case 'n': c = '\n'; break;
                        case 't': c = '\t'; break;
                        case '\'': c = '\''; break;
                        case '\\': c = '\\'; break;
                        default: c = esc; break;
                    }
                }
                return Integer.toString((int) c);
            }
        }
        return lexeme == null ? "" : lexeme;
    }
}


    // Atom model
    enum OpCode { ADD, SUB, MUL, DIV, JMP, NEG, LBL, TST, MOV }


    static class Atom 
    {
        final OpCode op;
        final String left;   // null or "" when not used
        final String right;  // null or ""
        final String result; // null or ""
        final Integer cmp;   // only for TST, else null
        final String dest;   // only for JMP/LBL/TST, else null

        Atom(OpCode op, String left, String right, String result) 
        {
            this(op, left, right, result, null, null);
        }

        Atom(OpCode op, String left, String right, String result, Integer cmp, String dest) 
        {
            this.op = op;
            this.left = left;
            this.right = right;
            this.result = result;
            this.cmp = cmp;
            this.dest = dest;
        }
        @Override public String toString() 
        {
            // Print in tuple formats
            return switch (op) 
            {
                case ADD -> String.format("(ADD, %s, %s, %s)", nv(left), nv(right), nv(result));
                case SUB -> String.format("(SUB, %s, %s, %s)", nv(left), nv(right), nv(result));
                case MUL -> String.format("(MUL, %s, %s, %s)", nv(left), nv(right), nv(result));
                case DIV -> String.format("(DIV, %s, %s, %s)", nv(left), nv(right), nv(result));
                case NEG -> String.format("(NEG, %s, , %s)", nv(left), nv(result));
                case MOV -> String.format("(MOV, %s, , %s)", nv(left), nv(result));
                case JMP -> String.format("(JMP, , , , , %s)", nv(dest));
                case LBL -> String.format("(LBL, , , , , %s)", nv(dest));
                case TST -> String.format("(TST, %s, %s, , %d, %s)", nv(left), nv(right), cmp, nv(dest));
            };
        }
        private static String nv(String s) { return s == null ? "" : s; }
    }


    // Parser
    static class RDParser 
    { 
        private final TokenSource ts;
        private final List<Atom> code = new ArrayList<>();
        private int tempCounter = 0;

        private TokenType peekType() throws IOException 
        {
        return ts.peek().type;
        }

        RDParser(TokenSource ts) { this.ts = ts; }

        List<Atom> parseProgram() throws IOException 
        {
            while (peekType() != TokenType.EOF) 
            {
                if (peekType() == TokenType.SEMICOLON) 
                { 
                    ts.next(); continue; 
                }
                parseStatement();
            }
            return code;
        }

        // statements 
        
        private void parseStatement() throws IOException 
        {
            Token t = ts.peek();

            if (isKeyword("label")) 
            {
                ts.next();
                String label = expect(TokenType.IDENTIFIER, "label name").lexeme;
                expect(TokenType.SEMICOLON, ";");
                code.add(new Atom(OpCode.LBL, null, null, null, null, label));
                return;
            }

            if (isKeyword("goto")) 
            {
                ts.next();
                String dest = expect(TokenType.IDENTIFIER, "label after goto").lexeme;
                expect(TokenType.SEMICOLON, ";");
                code.add(new Atom(OpCode.JMP, null, null, null, null, dest));
                return;
            }

            if (isKeyword("if")) 
            {
                ts.next();
                expect(TokenType.LPAREN, "(");
                Cond c = parseCondition();
                expect(TokenType.RPAREN, ")");
                if (!isKeyword("goto")) error("expected 'goto' after if(condition)");
                ts.next();

                String dest = expect(TokenType.IDENTIFIER, "label after goto").lexeme;
                expect(TokenType.SEMICOLON, ";");
                code.add(new Atom(OpCode.TST, c.left, c.right, null, c.cmpCode, dest));
                return;
            }

            // assignment
            String lhs = expect(TokenType.IDENTIFIER, "assignment LHS identifier").lexeme;
            expect(TokenType.ASSIGN, "=");

            String val = parseExpr();
            expect(TokenType.SEMICOLON, ";");
            
            code.add(new Atom(OpCode.MOV, val, null, lhs));
        }


        // conditions 
        static class Cond 
        {
            final String left, right;
            final int cmpCode;

            Cond(String l,String r,int c)
            {
                left=l;
                right=r;
                cmpCode=c;
            } 
        }


        private Cond parseCondition() throws IOException 
        {
            String left = parseExpr();
            TokenType op = peekType();
            int code;

            switch (op) 
            {
                case EQ -> { ts.next(); code = 1; }
                case LT -> { ts.next(); code = 2; }
                case GT -> { ts.next(); code = 3; }
                case LE -> { ts.next(); code = 4; }
                case GE -> { ts.next(); code = 5; }
                case NE -> { ts.next(); code = 6; }
                default -> throw error("expected comparison operator");
            }
            String right = parseExpr();
            return new Cond(left, right, code);
        }


        // Expressions
        private String parseExpr() throws IOException 
        {
            String left = parseTerm();

            while (peekType() == TokenType.PLUS || peekType() == TokenType.MINUS) 
            {
                TokenType op = ts.next().type;
                String right = parseTerm();
                String t = newTemp();
                code.add(new Atom(op == TokenType.PLUS ? OpCode.ADD : OpCode.SUB, left, right, t));
                left = t;
            }

            return left;
        }


        private String parseTerm() throws IOException 
        {
            String left = parseFactor();

            while (peekType() == TokenType.STAR || peekType() == TokenType.SLASH) 
            {
                TokenType op = ts.next().type;
                String right = parseFactor();
                String t = newTemp();
                code.add(new Atom(op == TokenType.STAR ? OpCode.MUL : OpCode.DIV, left, right, t));
                left = t;
            }

            return left;
        }


        private String parseFactor() throws IOException 
        {
            Token t = ts.peek();
            switch (t.type) 
            {
                case INT_LITERAL, FLOAT_LITERAL -> { ts.next(); return t.lexeme; }
                case IDENTIFIER -> { ts.next(); return t.lexeme; }
                case LPAREN -> { ts.next(); String v = parseExpr(); expect(TokenType.RPAREN, ")"); return v; }
                case MINUS -> { ts.next(); String v = parseFactor(); String tmp = newTemp(); code.add(new Atom(OpCode.NEG, v, null, tmp)); return tmp; }
                default -> throw errorAt(t, "unexpected token in expression: " + t.type);
            }
        }


        // helpers
        private String newTemp() 
        { 
            return "t" + (++tempCounter); 
        }


        private boolean isKeyword(String k) throws IOException 
        {
            Token t = ts.peek();
            return (t.type == TokenType.KEYWORD || t.type == TokenType.IDENTIFIER) && k.equals(t.lexeme);
        }


        private Token expect(TokenType type, String human) throws IOException 
        {
            Token t = ts.next();

            if (t.type != type) throw errorAt(t, "expected " + human + ", saw " + t.type + (t.lexeme.isEmpty()?"":" ("+t.lexeme+")"));
            return t;
        }


        private RuntimeException error(String msg) throws IOException 
        {
            return errorAt(ts.peek(), msg); 
        }


        private RuntimeException errorAt(Token t, String msg) 
        {
            return new RuntimeException("Parse error at "+t.line+":"+t.col+": "+msg); 
        }
    }



    // Main
    public static void main(String[] args) throws Exception 
    {
        Reader reader = (args.length > 0)
                ? new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8)
                : new InputStreamReader(System.in, StandardCharsets.UTF_8);

        // Swap SimpleScanner with an adapter to your Phase 1 scanner if desired.
        TokenSource ts = new ScannerAdapter(reader);
        RDParser p = new RDParser(ts);
        List<Atom> atoms = p.parseProgram();

        // Output atoms to STDOUT, one per line
        for (Atom a : atoms) 
        {
            System.out.println(a);
        }
    }
}
