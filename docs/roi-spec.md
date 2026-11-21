# Register of Information: column spec

Generated from `shared/src/commonMain/kotlin/dorai/shared/roi/RoiSpec.kt`, which follows CIR (EU) 2024/2956 Annex I
(bundled at `server/src/main/resources/ai/regulations/cir-2024-2956.pdf`). `RoiSpec.kt` is the source; change it there.

Need: when the column must be filled. Closed lists use the CIR option keys; the export maps them to EBA taxonomy codes.

## B_01.01 Entity maintaining the register of information

| Column | Name | Type | Need |
|---|---|---|---|
| B_01.01.0010 | LEI of the entity maintaining the register | Lei | Mandatory |
| B_01.01.0020 | Name of the entity | Text | Mandatory |
| B_01.01.0030 | Country of the entity | Country | Mandatory |
| B_01.01.0040 | Type of entity | Choice | Mandatory |
| B_01.01.0050 | Competent authority | Text | Mandatory in case of reporting |
| B_01.01.0060 | Date of the reporting | Date | Mandatory in case of reporting |

## B_01.02 List of entities within the scope of the register of information

| Column | Name | Type | Need |
|---|---|---|---|
| B_01.02.0010 | LEI of the entity | Lei | Mandatory |
| B_01.02.0020 | Name of the entity | Text | Mandatory |
| B_01.02.0030 | Country of the entity | Country | Mandatory |
| B_01.02.0040 | Type of entity | Choice | Mandatory |
| B_01.02.0050 | Hierarchy of the entity within the group | Choice | Mandatory |
| B_01.02.0060 | LEI of the direct parent undertaking | Lei | Mandatory |
| B_01.02.0070 | Date of last update | Date | Mandatory |
| B_01.02.0080 | Date of integration in the register of information | Date | Mandatory |
| B_01.02.0090 | Date of deletion in the register of information | Date | Mandatory |
| B_01.02.0100 | Currency | Currency | Mandatory only if the value of total assets is reported |
| B_01.02.0110 | Value of total assets | Monetary | Mandatory if the entity is a financial entity |

## B_01.03 List of branches

| Column | Name | Type | Need |
|---|---|---|---|
| B_01.03.0010 | Identification code of the branch | Text | Mandatory |
| B_01.03.0020 | LEI of the head office of the branch | Lei | Mandatory |
| B_01.03.0030 | Name of the branch | Text | Mandatory |
| B_01.03.0040 | Country of the branch | Country | Mandatory |

## B_02.01 Contractual arrangements: general information

| Column | Name | Type | Need |
|---|---|---|---|
| B_02.01.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_02.01.0020 | Type of contractual arrangement | Choice | Mandatory |
| B_02.01.0030 | Overarching contractual arrangement reference number | Text | Mandatory for a subsequent or associated arrangement |
| B_02.01.0040 | Currency of the amount reported in B_02.01.0050 | Currency | Mandatory |
| B_02.01.0050 | Annual expense or estimated cost of the contractual arrangement for the past year | Monetary | Mandatory |

## B_02.02 Contractual arrangements: specific information

| Column | Name | Type | Need |
|---|---|---|---|
| B_02.02.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_02.02.0020 | LEI of the entity making use of the ICT service(s) | Lei | Mandatory |
| B_02.02.0030 | Identification code of the ICT third-party service provider | Text | Mandatory |
| B_02.02.0040 | Type of code to identify the ICT third-party service provider | CodeType | Mandatory |
| B_02.02.0050 | Function identifier | FunctionId | Mandatory |
| B_02.02.0060 | Type of ICT services | Choice | Mandatory |
| B_02.02.0070 | Start date of the contractual arrangement | Date | Mandatory |
| B_02.02.0080 | End date of the contractual arrangement | Date | Mandatory |
| B_02.02.0090 | Reason of the termination or ending of the contractual arrangement | Choice | Mandatory if the contractual arrangement is terminated |
| B_02.02.0100 | Notice period for the entity making use of the ICT service(s) | NaturalNumber | Mandatory if the ICT service supports a critical or important function |
| B_02.02.0110 | Notice period for the ICT third-party service provider | NaturalNumber | Mandatory if the ICT service supports a critical or important function |
| B_02.02.0120 | Country of the governing law of the contractual arrangement | Country | Mandatory if the ICT service supports a critical or important function |
| B_02.02.0130 | Country of provision of the ICT services | Country | Mandatory if the ICT service supports a critical or important function |
| B_02.02.0140 | Storage of data | YesNo | Mandatory if the ICT service supports a critical or important function |
| B_02.02.0150 | Location of the data at rest (storage) | Country | Mandatory if 'Yes' is reported in B_02.02.0140 |
| B_02.02.0160 | Location of management of the data (processing) | Country | Mandatory if the ICT service is based on or foresees data processing |
| B_02.02.0170 | Sensitiveness of the data stored by the ICT third-party service provider | Choice | Mandatory if the provider stores data and the service supports a critical or important function |
| B_02.02.0180 | Level of reliance on the ICT service supporting the critical or important function | Choice | Mandatory if the ICT service supports a critical or important function |

## B_02.03 List of intra-group contractual arrangements

| Column | Name | Type | Need |
|---|---|---|---|
| B_02.03.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_02.03.0020 | Contractual arrangement reference number linked to B_02.03.0010 | Text | Mandatory |

## B_03.01 Entities signing the contractual arrangements for receiving ICT service(s)

| Column | Name | Type | Need |
|---|---|---|---|
| B_03.01.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_03.01.0020 | LEI of the entity signing the contractual arrangement | Lei | Mandatory |

## B_03.02 ICT third-party service providers signing the contractual arrangements

| Column | Name | Type | Need |
|---|---|---|---|
| B_03.02.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_03.02.0020 | Identification code of the ICT third-party service provider | Text | Mandatory |
| B_03.02.0030 | Type of code to identify the ICT third-party service provider | CodeType | Mandatory |

## B_03.03 Entities signing the contractual arrangements for providing ICT service(s)

| Column | Name | Type | Need |
|---|---|---|---|
| B_03.03.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_03.03.0020 | LEI of the entity providing ICT services | Lei | Mandatory |

## B_04.01 Entities making use of the ICT services

| Column | Name | Type | Need |
|---|---|---|---|
| B_04.01.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_04.01.0020 | LEI of the entity making use of the ICT service(s) | Lei | Mandatory |
| B_04.01.0030 | Nature of the entity making use of the ICT service(s) | Choice | Mandatory |
| B_04.01.0040 | Identification code of the branch | Text | Mandatory if the entity making use of the service is a branch |

## B_05.01 ICT third-party service providers

| Column | Name | Type | Need |
|---|---|---|---|
| B_05.01.0010 | Identification code of the ICT third-party service provider | Text | Mandatory |
| B_05.01.0020 | Type of code to identify the ICT third-party service provider | CodeType | Mandatory |
| B_05.01.0030 | Additional identification code of the ICT third-party service provider | Text | Optional |
| B_05.01.0040 | Type of the additional identification code | CodeType | Mandatory if B_05.01.0030 is reported |
| B_05.01.0050 | Legal name of the ICT third-party service provider | Text | Mandatory |
| B_05.01.0060 | Name of the ICT third-party service provider in Latin alphabet | Text | Mandatory |
| B_05.01.0070 | Type of person of the ICT third-party service provider | Choice | Mandatory |
| B_05.01.0080 | Country of the ICT third-party service provider's headquarters | Country | Mandatory |
| B_05.01.0090 | Currency of the amount reported in B_05.01.0100 | Currency | Mandatory if B_05.01.0100 is reported |
| B_05.01.0100 | Total annual expense or estimated cost of the ICT third-party service provider | Monetary | Mandatory if the provider is a direct ICT third-party service provider |
| B_05.01.0110 | Identification code of the provider's ultimate parent undertaking | Text | Mandatory if the provider is not the ultimate parent undertaking |
| B_05.01.0120 | Type of code to identify the provider's ultimate parent undertaking | CodeType | Mandatory if the provider is not the ultimate parent undertaking |

## B_05.02 ICT service supply chains

| Column | Name | Type | Need |
|---|---|---|---|
| B_05.02.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_05.02.0020 | Type of ICT services | Choice | Mandatory |
| B_05.02.0030 | Identification code of the ICT third-party service provider | Text | Mandatory |
| B_05.02.0040 | Type of code to identify the ICT third-party service provider | CodeType | Mandatory |
| B_05.02.0050 | Rank | NaturalNumber | Mandatory |
| B_05.02.0060 | Identification code of the recipient of sub-contracted ICT services | Text | Mandatory, not applicable for rank 1 |
| B_05.02.0070 | Type of code to identify the recipient of sub-contracted ICT services | CodeType | Mandatory, not applicable for rank 1 |

## B_06.01 Functions identification

| Column | Name | Type | Need |
|---|---|---|---|
| B_06.01.0010 | Function identifier | FunctionId | Mandatory |
| B_06.01.0020 | Licensed activity | Choice | Mandatory |
| B_06.01.0030 | Function name | Text | Mandatory |
| B_06.01.0040 | LEI of the entity | Lei | Mandatory |
| B_06.01.0060 | Criticality or importance assessment | Choice | Mandatory |
| B_06.01.0070 | Reasons for criticality or importance | Text | Optional |
| B_06.01.0080 | Date of the last assessment of criticality or importance | Date | Mandatory |
| B_06.01.0090 | Recovery time objective of the function (hours) | NaturalNumber | Mandatory |
| B_06.01.0100 | Recovery point objective of the function (hours) | NaturalNumber | Mandatory |
| B_06.01.0110 | Impact of discontinuing the function | Choice | Mandatory |

## B_07.01 Assessment of the ICT services

| Column | Name | Type | Need |
|---|---|---|---|
| B_07.01.0010 | Contractual arrangement reference number | Text | Mandatory |
| B_07.01.0020 | Identification code of the ICT third-party service provider | Text | Mandatory |
| B_07.01.0030 | Type of code to identify the ICT third-party service provider | CodeType | Mandatory |
| B_07.01.0040 | Type of ICT services | Choice | Mandatory |
| B_07.01.0050 | Substitutability of the ICT third-party service provider | Choice | Mandatory |
| B_07.01.0060 | Reason where the provider is not or hardly substitutable | Choice | Mandatory if 'not substitutable' or 'highly complex substitutability' is reported |
| B_07.01.0070 | Date of the last audit on the ICT third-party service provider | Date | Mandatory |
| B_07.01.0080 | Existence of an exit plan | YesNo | Mandatory |
| B_07.01.0090 | Possibility of reintegration of the contracted ICT service | Choice | Mandatory |
| B_07.01.0100 | Impact of discontinuing the ICT services | Choice | Mandatory |
| B_07.01.0110 | Are there alternative ICT third-party service providers identified? | Choice | Mandatory |
| B_07.01.0120 | Identification of alternative ICT third-party service providers | Text | Optional |

## B_99.01 Definitions from entities making use of the ICT services

| Column | Name | Type | Need |
|---|---|---|---|
| B_99.01.C0010 | Column code | Text | Mandatory |
| B_99.01.C0020 | Column name | Text | Mandatory |
| B_99.01.C0030 | Option | Text | Mandatory |
| B_99.01.C0040 | Description or internal definition of the option | Text | Mandatory |
