# Functional Group Filter Node

Filter molecules based on the presence and count of specified functional groups using SMARTS patterns. This node is a splitter that routes molecules passing all criteria to one output port and failing molecules to another.

---

## What It Does

The Functional Group Filter Node accepts a list of functional group definitions, each consisting of a SMARTS pattern and count constraints (minimum and maximum). For each molecule, it counts the number of substructure matches for each defined functional group. If the molecule satisfies all functional group criteria (all counts within the specified ranges), it is written to the "pass" port; otherwise it goes to the "fail" port.

**Key Points:**
- Two output ports: "pass" and "fail"
- Multiple functional groups can be specified (all must be satisfied — AND logic)
- Each group has an independent minCount and maxCount
- maxCount of -1 means no upper limit
- SMARTS patterns are parsed once and reused for all rows
- Rows with invalid or empty SMILES are sent to the "fail" port

---

## Configuration

### Input Ports
| Port | Description |
|------|-------------|
| **input** | Input table containing a column with SMILES strings |

### Output Ports
| Port | Description |
|------|-------------|
| **pass** | Table containing molecules that satisfy all functional group criteria |
| **fail** | Table containing molecules that do not satisfy the criteria |

### Properties
| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| **smilesColumn** | String | Yes | - | Name of the column containing SMILES strings |
| **functionalGroups** | Array | Yes | - | List of functional group definitions |

Each functional group definition:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| **name** | String | Yes | - | Name of the functional group |
| **smarts** | String | Yes | - | SMARTS pattern defining the group |
| **minCount** | Integer | No | 1 | Minimum number of matches required |
| **maxCount** | Integer | No | -1 | Maximum matches allowed (-1 = no limit) |

---

## Example

**Input Table:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| c1ccccc1 | benzene |
| Oc1cc(O)ccc1 | catechol |

**Configuration:**
- **smilesColumn**: `smiles`
- **functionalGroups**: `[{"name": "Hydroxyl", "smarts": "[OH]", "minCount": 1, "maxCount": -1}]`

**Pass Output:**

| smiles | name |
|--------|------|
| c1ccc(O)cc1 | phenol |
| Oc1cc(O)ccc1 | catechol |

**Fail Output:**

| smiles | name |
|--------|------|
| c1ccccc1 | benzene |

---

## Tips & Warnings

- **AND Logic**: All functional group criteria must be satisfied for a molecule to pass. If you need OR logic, use multiple instances of this node.
- **Count Ranges**: Set minCount to 0 and maxCount to 0 to exclude molecules containing a specific group.
- **Invalid SMARTS**: An invalid SMARTS pattern in any functional group definition will cause the node to throw an error before processing.
- **Memory Safety**: All SMARTS pattern molecules are deleted after processing; per-row molecules are deleted in `finally` blocks.

---

## Technical Details

- **Algorithm**: Parse all SMARTS patterns once, then sequential row processing with match counting
- **Memory**: Processes one row at a time (suitable for large datasets); SMARTS patterns held in memory
- **Progress**: Reports 100% on completion
- **Resource Management**: SMARTS patterns deleted in outer `finally` block; per-row molecules deleted in inner `finally` blocks; output writers closed in `finally` block
