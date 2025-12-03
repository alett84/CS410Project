import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
  CS410 – Phase 3: Code Generator
  Authors: Aidan Lett, Kumail Abbas
  Reviewer: Julia

*/
public class CodeGenerator {

    // Opcodes
    private static final int OP_CLR = 0;
    private static final int OP_ADD = 1;
    private static final int OP_SUB = 2;
    private static final int OP_MUL = 3;
    private static final int OP_DIV = 4;
    private static final int OP_JMP = 5;
    private static final int OP_CMP = 6;
    private static final int OP_LOD = 7;
    private static final int OP_STO = 8;
    private static final int OP_HLT = 9;

    // Comparison codes
    private static final int CMP_ALWAYS = 0;

    // Parsed atom model (mirrors Parser.Atom toString layout)
    private static class Atom {
        final String op;
        final String left;
        final String right;
        final String result;
        final Integer cmp;
        final String dest;

        Atom(String op, String left, String right, String result, Integer cmp, String dest) {
            this.op = op;
            this.left = left;
            this.right = right;
            this.result = result;
            this.cmp = cmp;
            this.dest = dest;
        }
    }

    // Symbol bookkeeping
private final Map<String, Integer> memoryMap = new HashMap<>();
private final Map<String, Integer> regMap = new HashMap<>();
private int nextMem = 0;
private int nextReg = 0;

// Use Phase 3B label table for instruction addresses
private final LabelTable labelTable = new LabelTable();
    // Public entrypoint
    public static void main(String[] args) throws Exception {
        String inputPath = null;
        String outputPath = null;

        for (String a : args) {
            if (a.startsWith("--out=")) {
                outputPath = a.substring("--out=".length());
            } else {
                inputPath = a;
            }
        }

        Reader r = (inputPath == null)
                ? new InputStreamReader(System.in, StandardCharsets.UTF_8)
                : new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8);

        Writer w = (outputPath == null)
                ? new OutputStreamWriter(System.out, StandardCharsets.UTF_8)
                : new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8);

        CodeGenerator gen = new CodeGenerator();
        gen.run(r, w);
        w.flush();
    }

    private void run(Reader reader, Writer out) throws Exception {
        List<Atom> atoms = readAtoms(reader);
        computeLabelAddresses(atoms);
        List<Integer> instructions = emitInstructions(atoms);

        for (Integer inst : instructions) {
            out.write(toBinary32(inst));
            out.write(System.lineSeparator());
        }
    }

    private List<Atom> readAtoms(Reader reader) throws Exception {
        List<Atom> atoms = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!line.startsWith("(") || !line.endsWith(")")) continue; // ignore malformed

                String inner = line.substring(1, line.length() - 1);
                String[] parts = inner.split("\\s*,\\s*");
                if (parts.length == 0) continue;

                String op = parts[0];
                switch (op) {
                    case "ADD":
                    case "SUB":
                    case "MUL":
                    case "DIV":
                    case "MOV":
                    case "NEG": {
                        // (OP, left, right, result)
                        String left = parts.length > 1 ? emptyToNull(parts[1]) : null;
                        String right = parts.length > 2 ? emptyToNull(parts[2]) : null;
                        String result = parts.length > 3 ? emptyToNull(parts[3]) : null;
                        atoms.add(new Atom(op, left, right, result, null, null));
                        break;
                    }
                    case "JMP": {
                        // (JMP, , , , , dest)
                        String dest = parts.length > 5 ? emptyToNull(parts[5]) : (parts.length > 1 ? emptyToNull(parts[1]) : null);
                        atoms.add(new Atom(op, null, null, null, null, dest));
                        break;
                    }
                    case "LBL": {
                        String dest = parts.length > 5 ? emptyToNull(parts[5]) : (parts.length > 1 ? emptyToNull(parts[1]) : null);
                        atoms.add(new Atom(op, null, null, null, null, dest));
                        break;
                    }
                    case "TST": {
                        // (TST, left, right, , cmp, dest)
                        String left = parts.length > 1 ? emptyToNull(parts[1]) : null;
                        String right = parts.length > 2 ? emptyToNull(parts[2]) : null;
                        Integer cmp = null;
                        if (parts.length > 4 && !parts[4].isEmpty()) {
                            cmp = Integer.parseInt(parts[4]);
                        }
                        String dest = parts.length > 5 ? emptyToNull(parts[5]) : null;
                        atoms.add(new Atom(op, left, right, null, cmp, dest));
                        break;
                    }
                    default:
                        // ignore unknown ops
                        break;
                }
            }
        }
        return atoms;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    // First pass (Part C): use LabelTable to compute instruction address per label
private void computeLabelAddresses(List<Atom> atoms) {
    // Start fresh for each program
    labelTable.reset();

    for (Atom a : atoms) {
        if ("LBL".equals(a.op)) {
            // Record label at current instruction address
            if (a.dest != null && !a.dest.isEmpty()) {
                labelTable.addLabel(a.dest, labelTable.getCurrentAddress());
            }
            // Do NOT advance address for a pure label
        } else {
            // This atom will emit instructionCost(a) machine instructions
            int cost = instructionCost(a);
            if (cost > 0) {
                labelTable.incrementAddress(cost);
            }
        }
    }

    labelTable.markFirstPassComplete();
}

    private int instructionCost(Atom a) {
        switch (a.op) {
            case "MOV": return 2;
            case "ADD":
            case "SUB":
            case "MUL":
            case "DIV": return 3;
            case "NEG": return 3;
            case "TST": return 3;
            case "JMP": return 2;
            default: return 0;
        }
    }

    // Second pass: emit instructions as 32-bit ints
    private List<Integer> emitInstructions(List<Atom> atoms) {
        List<Integer> out = new ArrayList<>();
        for (Atom a : atoms) {
            switch (a.op) {
                case "MOV": {
                    int r = regFor(symOrResult(a.result));
                    int addrSrc = addressFor(symOrResult(a.left));
                    out.add(encodeAbsolute(OP_LOD, CMP_ALWAYS, r, addrSrc));
                    int addrDst = addressFor(symOrResult(a.result));
                    out.add(encodeAbsolute(OP_STO, CMP_ALWAYS, r, addrDst));
                    break;
                }
                case "ADD":
                case "SUB":
                case "MUL":
                case "DIV": {
                    int r = regFor(symOrResult(a.result));
                    int addrL = addressFor(symOrResult(a.left));
                    int addrR = addressFor(symOrResult(a.right));
                    out.add(encodeAbsolute(OP_LOD, CMP_ALWAYS, r, addrL));
                    int op = opCodeFor(a.op);
                    out.add(encodeAbsolute(op, CMP_ALWAYS, r, addrR));
                    int addrDst = addressFor(symOrResult(a.result));
                    out.add(encodeAbsolute(OP_STO, CMP_ALWAYS, r, addrDst));
                    break;
                }
                case "NEG": {
                    int r = regFor(symOrResult(a.result));
                    int addrSrc = addressFor(symOrResult(a.left));
                    out.add(encodeAbsolute(OP_CLR, CMP_ALWAYS, r, 0));
                    out.add(encodeAbsolute(OP_SUB, CMP_ALWAYS, r, addrSrc)); // 0 - src
                    int addrDst = addressFor(symOrResult(a.result));
                    out.add(encodeAbsolute(OP_STO, CMP_ALWAYS, r, addrDst));
                    break;
                }
                case "TST": {
                    int r = regFor(symOrResult(a.left));
                    int addrL = addressFor(symOrResult(a.left));
                    int addrR = addressFor(symOrResult(a.right));
                    out.add(encodeAbsolute(OP_LOD, CMP_ALWAYS, r, addrL));
                    int cmpCode = a.cmp == null ? CMP_ALWAYS : a.cmp;
                    out.add(encodeAbsolute(OP_CMP, cmpCode, r, addrR));
                    int destAddr = resolveLabel(a.dest);
                    out.add(encodeAbsolute(OP_JMP, CMP_ALWAYS, 0, destAddr));
                    break;
                }
                case "JMP": {
                    int destAddr = resolveLabel(a.dest);
                    out.add(encodeAbsolute(OP_CMP, CMP_ALWAYS, 0, 0)); // ensure flag true
                    out.add(encodeAbsolute(OP_JMP, CMP_ALWAYS, 0, destAddr));
                    break;
                }
                case "LBL":
                    // no code emitted
                    break;
                default:
                    // ignore unknown ops
                    break;
            }
        }
        // finish program with HLT to be safe
        out.add(encodeAbsolute(OP_HLT, CMP_ALWAYS, 0, 0));
        return out;
    }

    private String symOrResult(String s) {
        return s == null ? "" : s;
    }

    private int addressFor(String sym) {
        if (sym == null || sym.isEmpty()) return 0;
        if (isNumber(sym)) {
            long v = Long.parseLong(sym.split("\\.")[0]);
            return (int) v;
        }
        return memoryMap.computeIfAbsent(sym, k -> nextMem++);
    }

    private int regFor(String sym) {
        if (sym == null || sym.isEmpty()) return 0;
        return regMap.computeIfAbsent(sym, k -> {
            int r = nextReg;
            nextReg = Math.min(15, nextReg + 1);
            return r;
        });
    }

    private int resolveLabel(String label) {
        if (label == null || label.isEmpty()) return 0;
    
        Integer addr = labelTable.getAddress(label);
        if (addr == null) {
            // throw new IllegalArgumentException("Undefined label: " + label);
            return 0;
        }
        return addr;
    }

    private int opCodeFor(String op) {
        switch (op) {
            case "ADD": return OP_ADD;
            case "SUB": return OP_SUB;
            case "MUL": return OP_MUL;
            case "DIV": return OP_DIV;
            default: return OP_ADD;
        }
    }

    // Encode in absolute mode: bits 0-3 opcode, bit4 mode=0, bits5-7 cmp, bits8-11 reg, bits12-31 address
    private int encodeAbsolute(int opcode, int cmp, int reg, int addr) {
        int value = 0;
        value |= (opcode & 0xF);
        value |= ((cmp & 0x7) << 5); // bit4 stays 0 for absolute mode
        value |= ((reg & 0xF) << 8);
        value |= ((addr & 0xFFFFF) << 12);
        return value;
    }

    private boolean isNumber(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) continue;
            if (!Character.isDigit(c)) return false;
        }
        return !s.isEmpty();
    }

    private String toBinary32(int value) {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 31; i >= 0; i--) {
            sb.append(((value >>> i) & 1) == 1 ? '1' : '0');
        }
        return sb.toString();
    }
}
