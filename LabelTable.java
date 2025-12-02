import java.util.*;

/*
  CS410 – Phase 3: Code Generator - Section B
  Label Table and First Pass Implementation

  Author(s): Julia Van Albert, 
  Reviewer: 
  
  This class implements the label table data structure and first pass logic
  for translating intermediate code atoms with label support.
*/

public class LabelTable {
    
    // Core label table: maps label names to their instruction addresses
    private final Map<String, Integer> labelAddresses;
    
    // Current instruction address counter (increments with each instruction)
    private int currentAddress;
    
    // Track if we're in first pass or subsequent passes
    private boolean firstPassComplete;
    
    /**
     * Constructor: Initialize empty label table
     */
    public LabelTable() {
        this.labelAddresses = new HashMap<>();
        this.currentAddress = 0;
        this.firstPassComplete = false;
    }
    
    /**
     * Add a label at the current instruction address
     * Called when encountering a label definition (e.g., "LOOP:")
     * 
     * @param labelName The name of the label (without the colon)
     * @throws IllegalArgumentException if label already exists
     */
    public void addLabel(String labelName) {
        if (labelAddresses.containsKey(labelName)) {
            throw new IllegalArgumentException("Duplicate label: " + labelName);
        }
        labelAddresses.put(labelName, currentAddress);
    }
    
    /**
     * Add a label at a specific address
     * Useful for manual address assignment
     * 
     * @param labelName The name of the label
     * @param address The instruction address
     * @throws IllegalArgumentException if label already exists
     */
    public void addLabel(String labelName, int address) {
        if (labelAddresses.containsKey(labelName)) {
            throw new IllegalArgumentException("Duplicate label: " + labelName);
        }
        labelAddresses.put(labelName, address);
    }
    
    /**
     * Get the address associated with a label
     * Used during code generation to resolve label references
     * 
     * @param labelName The label to look up
     * @return The instruction address, or null if label doesn't exist
     */
    public Integer getAddress(String labelName) {
        return labelAddresses.get(labelName);
    }
    
    /**
     * Check if a label exists in the table
     * 
     * @param labelName The label to check
     * @return true if label exists, false otherwise
     */
    public boolean hasLabel(String labelName) {
        return labelAddresses.containsKey(labelName);
    }
    
    /**
     * Get the current instruction address
     * 
     * @return Current address value
     */
    public int getCurrentAddress() {
        return currentAddress;
    }
    
    /**
     * Set the current instruction address
     * Useful for resetting or manual positioning
     * 
     * @param address The new current address
     */
    public void setCurrentAddress(int address) {
        this.currentAddress = address;
    }
    
    /**
     * Increment the current address by 1
     * Called after processing each instruction
     */
    public void incrementAddress() {
        this.currentAddress++;
    }
    
    /**
     * Increment the current address by a specific amount
     * 
     * @param amount Number of addresses to increment
     */
    public void incrementAddress(int amount) {
        this.currentAddress += amount;
    }
    
    /**
     * Mark first pass as complete
     * Helps track assembly state
     */
    public void markFirstPassComplete() {
        this.firstPassComplete = true;
    }
    
    /**
     * Check if first pass is complete
     * 
     * @return true if first pass finished, false otherwise
     */
    public boolean isFirstPassComplete() {
        return firstPassComplete;
    }
    
    /**
     * Reset the label table for a new assembly
     * Clears all labels and resets address counter
     */
    public void reset() {
        labelAddresses.clear();
        currentAddress = 0;
        firstPassComplete = false;
    }
    
    /**
     * Get the total number of labels in the table
     * 
     * @return Number of labels
     */
    public int size() {
        return labelAddresses.size();
    }
    
    /**
     * Get all label names in the table
     * Useful for debugging and validation
     * 
     * @return Set of all label names
     */
    public Set<String> getAllLabels() {
        return new HashSet<>(labelAddresses.keySet());
    }
    
    /**
     * Get a copy of the entire label table
     * Returns defensive copy to prevent external modification
     * 
     * @return Map of label names to addresses
     */
    public Map<String, Integer> getTableCopy() {
        return new HashMap<>(labelAddresses);
    }
    
    /**
     * Display the label table contents
     * Useful for debugging and verification
     * 
     * @return String representation of the table
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Label Table (").append(labelAddresses.size()).append(" entries):\n");
        sb.append("Current Address: ").append(currentAddress).append("\n");
        sb.append("-".repeat(40)).append("\n");
        
        // Sort labels by address for readable output
        labelAddresses.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .forEach(entry -> 
                sb.append(String.format("%-20s -> Address %04d\n", 
                    entry.getKey(), entry.getValue()))
            );
        
        return sb.toString();
    }
    
    /**
     * FIRST PASS: Process a list of intermediate code atoms
     * Builds the label table by scanning through all atoms
     * 
     * Integration with Code Generator:
     * - Identifies label definitions (ending with ':')
     * - Records label positions
     * - Tracks instruction addresses
     * - Generates placeholder instructions for label references
     * 
     * @param atoms List of intermediate code atoms
     * @return List of processed atoms (non-label atoms only)
     */
    public List<String> performFirstPass(List<String> atoms) {
        reset(); // Start fresh
        List<String> instructions = new ArrayList<>();
        
        for (String atom : atoms) {
            String trimmed = atom.trim();
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            
            // Check if this is a label definition
            if (trimmed.endsWith(":")) {
                // Extract label name (remove the colon)
                String labelName = trimmed.substring(0, trimmed.length() - 1);
                addLabel(labelName);
                // Don't increment address - label doesn't consume space
            } else {
                // This is an actual instruction
                instructions.add(trimmed);
                incrementAddress(); // Each instruction takes one address
            }
        }
        
        markFirstPassComplete();
        return instructions;
    }
    
    /**
     * Alternative first pass that works with structured Atom objects
     * (if your team uses an Atom class instead of raw strings)
     * 
     * @param atoms List of Atom objects
     * @return List of instruction atoms (non-labels)
     */
    public <T> List<T> performFirstPass(List<T> atoms, AtomProcessor<T> processor) {
        reset();
        List<T> instructions = new ArrayList<>();
        
        for (T atom : atoms) {
            if (processor.isLabel(atom)) {
                String labelName = processor.getLabelName(atom);
                addLabel(labelName);
            } else {
                instructions.add(atom);
                incrementAddress();
            }
        }
        
        markFirstPassComplete();
        return instructions;
    }
    
    /**
     * Interface for processing different atom representations
     * Allows integration with various code generator designs
     */
    public interface AtomProcessor<T> {
        boolean isLabel(T atom);
        String getLabelName(T atom);
    }
    
    /**
     * Validate label table integrity
     * Checks for common issues
     * 
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        
        // Check for negative addresses
        for (Map.Entry<String, Integer> entry : labelAddresses.entrySet()) {
            if (entry.getValue() < 0) {
                errors.add("Label '" + entry.getKey() + 
                          "' has negative address: " + entry.getValue());
            }
        }
        
        // Check for empty label names
        for (String label : labelAddresses.keySet()) {
            if (label == null || label.isEmpty()) {
                errors.add("Found empty or null label name");
            }
        }
        
        return errors;
    }
    
    // ========== DEMO AND TESTING ==========
    
    /**
     * Demonstration of label table usage
     */
    /* 
    public static void main(String[] args) {
        System.out.println("CS410 Phase 3 - Section B: Label Table Demo\n");
        
        // Example 1: Basic usage
        LabelTable table = new LabelTable();
        
        System.out.println("=== Example 1: Manual Label Addition ===");
        table.addLabel("START", 0);
        table.setCurrentAddress(5);
        table.addLabel("LOOP");
        table.incrementAddress();
        table.addLabel("END");
        
        System.out.println(table);
        
        // Example 2: First pass simulation
        System.out.println("\n=== Example 2: First Pass Simulation ===");
        LabelTable table2 = new LabelTable();
        
        List<String> sampleAtoms = Arrays.asList(
            "// Sample program",
            "LOD R1 100",
            "LOD R2 50",
            "LOOP:",
            "ADD R1 R2",
            "CMP GT R1 200",
            "JMP END",
            "MUL R1 2",
            "JMP LOOP",
            "END:",
            "STO 500 R1",
            "HLT"
        );
        
        List<String> instructions = table2.performFirstPass(sampleAtoms);
        
        System.out.println(table2);
        System.out.println("\nInstructions to process in second pass:");
        for (int i = 0; i < instructions.size(); i++) {
            System.out.printf("%04d: %s\n", i, instructions.get(i));
        }
        
        // Example 3: Label lookup
        System.out.println("\n=== Example 3: Label Address Lookup ===");
        System.out.println("Address of 'LOOP': " + table2.getAddress("LOOP"));
        System.out.println("Address of 'END': " + table2.getAddress("END"));
        System.out.println("Has 'START': " + table2.hasLabel("START"));
        System.out.println("Has 'LOOP': " + table2.hasLabel("LOOP"));
        
        // Example 4: Validation
        System.out.println("\n=== Example 4: Validation ===");
        List<String> errors = table2.validate();
        if (errors.isEmpty()) {
            System.out.println("✓ Label table is valid");
        } else {
            System.out.println("✗ Validation errors:");
            errors.forEach(err -> System.out.println("  - " + err));
        }
        
        System.out.println("\n=== Integration Notes ===");
        System.out.println("This label table integrates with the code generator by:");
        System.out.println("1. Being called during first pass to record all labels");
        System.out.println("2. Providing addresses during second pass for label references");
        System.out.println("3. Enabling placeholder instruction generation");
        System.out.println("4. Supporting both single-pass (with fixup) and multi-pass strategies");
    }
        */
}
