
import { describe, it, expect } from 'vitest';

describe('Sample Test Suite', () => {
    it('should add two numbers correctly', () => {
        expect(1 + 1).toBe(2);
    });
    
    it('should subtract two numbers correctly', () => {
        expect(5 - 3).toBe(2);
    });
    
    it('should multiply two numbers correctly', () => {
        expect(2 * 3).toBe(6);
    });
    
    it('should divide two numbers correctly', () => {
        expect(10 / 2).toBe(5);
    });
    
    it('should handle string concatenation', () => {
        expect('hello ' + 'world').toBe('hello world');
    });
});
