import java.io.*; 
import java.nio.charset.StandardCharsets; 
import java.util.*; 





public class Scanner{ 

    // TOKEN MODEL
    enum TokenType { 
    //keywords and identifiers 
    KEYWORD, IDENTIFIER,
    //literals 
    INT_LITERAL, FLOAT_LITERAL,
    //operators + punctuation (GT = greater than, ect) 
    ASSIGN, EQ, LT, GT, LE, GE, NE, NOT,
    PLUS, MINUS, STAR, SLASH,
    LPAREN, RPAREN, SEMICOLON, DOT,
    //Specials  
    EOF, ERROR
    }


    static class Token {
    final TokenType type; 
    final String lexeme; //concrete text (value for id/literal)
    final int line; 
    final int col; 
    Token(TokenType type, String lexeme, int line, int col){
        this.type = type; 
        this.lexeme = lexeme; 
        this.line = line; 
        this.col = col; 
    }
    @Override public String toString(){
        String val = switch (type){
            case IDENTIFIER, KEYWORD, INT_LITERAL, FLOAT_LITERAL -> " (" + lexeme + ")";
            default -> "";
        };
        return type + val + " @" + line + ":" + col;
     }
    }

    public static class ScannerEngine{ 
        //character classes (columns)
        //Adjust to match the FSM input alphabet(AIDAN)
        static final int CC_LETTER = 0;
        static final int CC_DIGIT = 1; 
        static final int CC_UNDERSCRORE = 2; 
        static final int CC_DOT = 3; 
        static final int CC_WS = 4;//Whitespace (space, tab, CR, LF)
        static final int CC_EQ_BANG_LT_GT = 5; // '=', '!', '<', '>' (2-char ops allowed)
        static final int CC_PLUS_MINUS_STAR_SLASH = 6; // +, -, *, /
        static final int CC_LPAREN = 7; //'('
        static final int CC_RPAREN = 8; // ')'
        static final int CC_SEMI = 9; //';'
        static final int CC_EOF = 10; //virtual EOF class used by the driver
        static final int CC_OTHER = 11; //ANYTHING ELSE 
        static final int CLASS_COUNT = 12;

        //states (rows)
        static final int S_START = 0; 
        static final int S_ID = 1; // [A-Z a-z_][A-Z a-z 0-9_]*
        static final int S_INT = 2;  // [0-9]+
        static final int S_FLOAT = 3; // [0-9]+ \.[0-9]+ 
        static final int S_DOT = 4; // '.' (AS A TOKEN)
        static final int S_OP_EQ = 5; // '=' OR '=='
        static final int S_OP_REL = 6; // '<' or '>' or gte or lte 
        static final int S_OP_BANG = 7; // '!' OR '!=' OR NOT
        static final int S_OP_PMSS = 8; // +, -, *, /
        static final int S_LPAREN = 9; 
        static final int S_RPAREN = 10; 
        static final int S_SEMI = 11; //';'
        static final int S_ERROR = 12; //error sink
        static final int S_EOF = 13; // EOF accept state
        static final int STATE_COUNT = 14;

        private final int[][] T = new int[STATE_COUNT][CLASS_COUNT];
        private final PushbackReader in; 
        private int line = 1, col = 0;
        private boolean deliveredEOF = false;
        
        private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "else", "while", "for", "return", 
            "int", "float", "void", "true", "false"
        ));

        ScannerEngine(Reader reader) {
            this.in = new PushbackReader(new BufferedReader(reader), 2);
            buildTransitions(); 
        }


        //build the state transition table T[state][charClass]
        private void buildTransitions() {
        //initialize all to -1 (no transition)
        for (int s = 0; s < STATE_COUNT; s++) Arrays.fill(T[s], -1);

        //START transitions
        T[S_START][CC_WS] = S_START; //Skip whitespace
        T[S_START][CC_LETTER] = S_ID;
        T[S_START][CC_UNDERSCRORE] = S_ID; 
        T[S_START][CC_DIGIT] = S_INT; 
        T[S_START][CC_DOT] = S_DOT; //dot as punctuation 
        T[S_START][CC_EQ_BANG_LT_GT] = S_OP_EQ; //will re-dispatch based on first lexeme char
        T[S_START][CC_PLUS_MINUS_STAR_SLASH] = S_OP_PMSS; 
        T[S_START][CC_LPAREN] = S_LPAREN; 
        T[S_START][CC_RPAREN] = S_RPAREN; 
        T[S_START][CC_SEMI] = S_SEMI; 
        T[S_START][CC_EOF] = S_EOF; 
        T[S_START][CC_OTHER] = S_ERROR; 

        //ID Transitions 
        T[S_ID][CC_LETTER] = S_ID; 
        T[S_ID][CC_DIGIT] = S_ID; 
        T[S_ID][CC_UNDERSCRORE] = S_ID; 
        //any other class -> no transition => accept identifier/keyword 

        //INT transitions
        T[S_INT][CC_DIGIT] = S_INT; 
        T[S_INT][CC_DOT] = S_FLOAT; // START FRACTION PART
        //others => accept INT

        //FLOAT transitions 
        T[S_FLOAT][CC_DIGIT] = S_FLOAT; 
        //others => accept FLOAT

        //DOT is single-char token => no outgoing transitions (accept)

        //Operators 
        //S_OP_EQ is entered on '=', '!', '<', '>'
        T[S_OP_EQ][CC_EQ_BANG_LT_GT] = -1; 

    }

    //Determine if a state is accepting (should emmit a token when we stop here)
    private boolean accepting(int state) {
        return switch (state) {
            case S_ID, S_INT, S_FLOAT, S_DOT, S_OP_EQ, S_OP_PMSS, S_LPAREN, S_RPAREN, S_SEMI, S_ERROR, S_EOF -> true;
            default -> false; 
        };
    }

    //Map accepting state + lexeme to a concrete TokenType 
    private TokenType tokenTypeFor(int state, String lexeme) {
        switch (state) {
            case S_ID: 
                return KEYWORDS.contains(lexeme) ? TokenType.KEYWORD : TokenType.IDENTIFIER; 
            case S_INT: return TokenType.INT_LITERAL; 
            case S_FLOAT: return TokenType.FLOAT_LITERAL; 
            case S_DOT: return TokenType.DOT; 
            case S_OP_PMSS:
                return switch (lexeme.charAt(0)) {
                    case '+' -> TokenType.PLUS; 
                    case '-' -> TokenType.MINUS; 
                    case '*' -> TokenType.STAR; 
                    case '/' -> TokenType.SLASH; 
                    default -> TokenType.ERROR; 
                };
            case S_LPAREN: return TokenType.LPAREN; 
            case S_RPAREN: return TokenType.RPAREN; 
            case S_SEMI: return TokenType.SEMICOLON; 
            case S_OP_EQ: {
                char c0 = lexeme.charAt(0);
                if(lexeme.length() == 2 && lexeme.charAt(1) == '=') {
                    return switch (c0) {
                        case '=' -> TokenType.EQ; 
                        case '<' -> TokenType.LE; 
                        case '>' -> TokenType.GE; 
                        case '!' -> TokenType.NE; 
                        default -> TokenType.ERROR; 
                    }; 
                }else{
                    return switch (c0) { 
                        case '=' -> TokenType.ASSIGN; 
                        case '<' -> TokenType.LT; 
                        case '>' -> TokenType.GT; 
                        case '!' -> TokenType.NOT; //accept standalone '!'
                        default -> TokenType.ERROR;     
                    };
                }
            }  
            case S_EOF: return TokenType.EOF; 
            case S_ERROR: return TokenType.ERROR; 
            default: return TokenType.ERROR;
        }
    } 

    //Classify a character into a character class column 
    private int classify(int ch) {
        if (ch == -1) return CC_EOF;
        char c = (char) ch; 
        if (c == '\n') { return CC_WS; }
        if (c == '\r' || c == '\t' || c == ' ') return CC_WS; 
        if (c == '_') return CC_UNDERSCRORE; 
        if (Character.isLetter(c)) return CC_LETTER; 
        if (Character.isDigit(c)) return CC_DIGIT; 
        if (c == '.') return CC_DOT; 
        if (c == '=' || c == '!' || c == '<' || c == '>') return CC_EQ_BANG_LT_GT;
        if (c == '+' || c == '-' || c == '*' || c == '/') return CC_PLUS_MINUS_STAR_SLASH;
        if (c == '(') return CC_LPAREN; 
        if (c == ')') return CC_RPAREN; 
        if (c == ';') return CC_SEMI; 
        return CC_OTHER; 
    }

    //Read next codepoint, updating line/col; return -1 on EOF
    private int read() throws IOException {
        int ch = in.read(); 
        if (ch == -1) { deliveredEOF = true; return -1; }
        if (ch == '\n') { line++; col = 0; } else { col++; }
        return ch; 
    }

    //Push back one character, restoring position counters
    private void unread(int ch) throws IOException {
        if (ch == -1) return; 
        in.unread(ch); 
        if (ch == '\n') { line--; /* col unknown; best-effort */}
        else { col = Math.max(0, col -1);}
    }

    // Core scanning loop: produce next token 
    Token nextToken() throws IOException {
        if (deliveredEOF) return new Token(TokenType.EOF, "", line, col); 

        StringBuilder lexeme = new  StringBuilder(); 
        int state = S_START; 

        //Skip leading whitespace between tokens 
        int ch; int chClass; 
        int tokStartLine, tokStartCol; 
        do{ 
            ch = read(); 
            chClass = classify(ch); 
        }while (chClass == CC_WS); 
        tokStartLine = line; tokStartCol = Math.max(1, col); 

        //If the first non-ws char is EOF
        if(chClass == CC_EOF) { 
            return new Token(TokenType.EOF, "", tokStartLine, tokStartCol); 
        }

        //Enter first state from START
        int next = T[S_START][chClass]; 
        if (next == -1) next = S_ERROR; 
        state = next; 
        lexeme.append((char)ch); 

        //Specialization:  resolve which OP state we actaully meant (EQ, REL, BANG)
        if (state == S_OP_EQ) {
            char c0 = (char) ch; 
            if (c0 == '<' || c0 == '>') state = S_OP_REL; 
            else if (c0 == '!') state = S_OP_BANG; 
            else state = S_OP_EQ; // '='
        }

        //Consume until no transition, when stopped, decide accept 
        while(true) { 
            //Decide lookahead transitions for operator families expecting optional '='
            if (state == S_OP_EQ || state == S_OP_REL || state == S_OP_BANG) {
                int la = read(); 
                if (la == '='){
                    lexeme.append('='); 
                    //emit combined operator token by staying in the same state and then breaking 
                }else{ 
                    unread(la);
                }
                break; //accept operator token 
            }

            int la = read(); 
            int laClass = classify(la); 
            int ns = (la == -1) ? -1 : T[state][laClass]; 

            if (ns == -1 || laClass == CC_WS){ 
                //if we hit ws, stop token here and push it back for outer skipper 
                if(laClass != CC_EOF && laClass != CC_WS) unread(la); 
                else if (laClass == CC_WS) unread(la); 
                break;
            }else{ 
                state = ns; 
                lexeme.append((char) la); 
            }
        }

        //Determine token type 
        TokenType type = tokenTypeFor(state, lexeme.toString()); 
        if (type == TokenType.ERROR) { 
            return new Token(TokenType.ERROR, lexeme.toString(), tokStartLine, tokStartCol); 
        }
        return new Token(type, lexeme.toString(), tokStartLine, tokStartCol);
    }

    //Scan all tokens 
    List<Token> scanAll() throws IOException { 
        List<Token> out = new ArrayList<>(); 
        while(true){ 
            Token t = nextToken(); 
            out.add(t); 
            if(t.type == TokenType.EOF) break; 
        }
        return out; 
    }
}





//MAIN IO WIRING
    public static void main(String[] args) throws Exception{ 
        Reader reader; 
        if (args.length > 0) { 
            reader = new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8); 
        }else{ 
            reader = new InputStreamReader(System.in, StandardCharsets.UTF_8); 
        }

        ScannerEngine engine = new ScannerEngine(reader); 
        List<Token> tokens = engine.scanAll(); 
        //Output token stream to STDOUT (type + optional value)
        for (Token t : tokens) { 
            //Format per requirement (a) class, (b) value (if any)
            switch (t.type) {
                case IDENTIFIER, KEYWORD, INT_LITERAL, FLOAT_LITERAL ->
                    System.out.println(t.type + "\t" + t.lexeme); 
                case EOF -> System.out.println("EOF"); 
                default -> System.out.println(t.type.name());
            }
        }
    }
}
