package com.univent.service;

import com.univent.model.dto.response.*;
import com.univent.model.entity.College;
import com.univent.model.entity.CollegeProgram;
import com.univent.model.entity.Review;
import com.univent.model.entity.SavedComparison;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComparisonService {

    private final CollegeRepository collegeRepository;
    private final ProgramRepository programRepository;
    private final CollegeProgramRepository collegeProgramRepository;
    private final ReviewRepository reviewRepository;
    private final SavedComparisonRepository savedComparisonRepository;
    private final CollegeService collegeService;
    private final ProgramService programService;

    // Weights for ranking calculation
    private static final BigDecimal REVIEW_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal PLACEMENT_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal ROI_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal INFRA_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal TEACHING_WEIGHT = new BigDecimal("0.05");

    @Transactional(readOnly = true)
    public CollegeComparisonResponse compareColleges(List<UUID> collegeIds, UUID programId) {
        if (collegeIds.size() < 2) {
            throw new RuntimeException("At least 2 colleges required for comparison");
        }
        if (collegeIds.size() > 4) {
            throw new RuntimeException("Maximum 4 colleges can be compared at once");
        }

        var program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found"));

        List<CollegeComparisonData> comparisonData = new ArrayList<>();

        for (UUID collegeId : collegeIds) {
            College college = collegeRepository.findById(collegeId)
                    .orElseThrow(() -> new RuntimeException("College not found: " + collegeId));

            CollegeProgram collegeProgram = collegeProgramRepository
                    .findByCollegeAndProgram(college, program)
                    .orElseThrow(() -> new RuntimeException(
                            program.getName() + " is not offered at " + college.getName()));

            CollegeComparisonData data = buildComparisonData(college, collegeProgram, program);
            comparisonData.add(data);
        }

        // Calculate weighted scores and rank
        Map<String, BigDecimal> weightedScores = calculateWeightedScores(comparisonData);
        rankColleges(comparisonData, weightedScores);

        // Build comparison metrics
        List<ComparisonMetric> metrics = buildComparisonMetrics(comparisonData);

        // Calculate ROI comparison
        ROIComparison roiComparison = calculateROIComparison(comparisonData);

        // Generate recommendation
        String recommendation = generateRecommendation(comparisonData, weightedScores);

        return CollegeComparisonResponse.builder()
                .collegeIds(collegeIds)
                .programId(programId)
                .colleges(comparisonData)
                .metrics(metrics)
                .roiComparison(roiComparison)
                .recommendation(recommendation)
                .weightedScores(weightedScores)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private CollegeComparisonData buildComparisonData(College college, CollegeProgram collegeProgram,
                                                      com.univent.model.entity.Program program) {
        // Get published reviews
        Page<Review> reviewsPage = reviewRepository.findByCollegeIdAndProgramIdAndStatus(
                college.getId(), program.getId(), ReviewStatus.PUBLISHED, Pageable.unpaged());
        List<Review> reviews = reviewsPage.getContent();

        BigDecimal avgRating = calculateAverageRating(reviews);
        int reviewCount = reviews.size();

        // Calculate would-recommend percentage
        long recommendCount = reviews.stream().filter(Review::getWouldRecommend).count();
        int wouldRecommendPercent = reviewCount > 0 ? (int) ((recommendCount * 100) / reviewCount) : 0;

        // Calculate average aspect ratings
        BigDecimal teachingQuality = calculateAverageAspect(reviews, "teachingQuality");
        BigDecimal infrastructure = calculateAverageAspect(reviews, "infrastructure");
        BigDecimal hostelLife = calculateAverageAspect(reviews, "hostelLife");
        BigDecimal campusLife = calculateAverageAspect(reviews, "campusLife");
        BigDecimal valueForMoney = calculateAverageAspect(reviews, "valueForMoney");

        // Calculate ROI score
        BigDecimal roiScore = calculateROIScore(collegeProgram);

        return CollegeComparisonData.builder()
                .college(collegeService.mapToResponse(college))
                .program(programService.mapToResponse(program))
                .overallRating(avgRating)
                .reviewCount(reviewCount)
                .placementPercentage(collegeProgram.getPlacementPercentage())
                .medianPackage(collegeProgram.getMedianPackage())
                .totalFees(collegeProgram.getFeesTotal())
                .teachingQuality(teachingQuality)
                .infrastructure(infrastructure)
                .hostelLife(hostelLife)
                .campusLife(campusLife)
                .valueForMoney(valueForMoney)
                .wouldRecommendPercent(wouldRecommendPercent)
                .roiScore(roiScore)
                .build();
    }

    private BigDecimal calculateAverageRating(List<Review> reviews) {
        if (reviews.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = reviews.stream()
                .map(r -> BigDecimal.valueOf(r.getOverallRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(reviews.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverageAspect(List<Review> reviews, String aspect) {
        if (reviews.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (Review review : reviews) {
            switch (aspect) {
                case "teachingQuality":
                    sum = sum.add(BigDecimal.valueOf(review.getTeachingQuality()));
                    break;
                case "infrastructure":
                    sum = sum.add(BigDecimal.valueOf(review.getInfrastructure()));
                    break;
                case "hostelLife":
                    sum = sum.add(BigDecimal.valueOf(review.getHostelLife()));
                    break;
                case "campusLife":
                    sum = sum.add(BigDecimal.valueOf(review.getCampusLife()));
                    break;
                case "valueForMoney":
                    sum = sum.add(BigDecimal.valueOf(review.getValueForMoney()));
                    break;
            }
        }
        return sum.divide(BigDecimal.valueOf(reviews.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateROIScore(CollegeProgram cp) {
        if (cp.getMedianPackage() == null || cp.getFeesTotal() == null) {
            return BigDecimal.ZERO;
        }
        // ROI = (Median Package * 4 - Total Fees) / Duration (in lakhs)
        BigDecimal fourYearEarning = cp.getMedianPackage().multiply(new BigDecimal("4"));
        BigDecimal netGain = fourYearEarning.subtract(cp.getFeesTotal());
        if (netGain.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        return netGain.divide(BigDecimal.valueOf(cp.getProgram().getDurationYears()), 0, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> calculateWeightedScores(List<CollegeComparisonData> data) {
        Map<String, BigDecimal> scores = new HashMap<>();

        for (CollegeComparisonData college : data) {
            // Normalize each metric to 0-100 scale
            BigDecimal normalizedRating = college.getOverallRating()
                    .divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            BigDecimal normalizedPlacement = college.getPlacementPercentage() != null ?
                    college.getPlacementPercentage() : BigDecimal.ZERO;

            BigDecimal normalizedROI = college.getRoiScore()
                    .divide(new BigDecimal("5000000"), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            normalizedROI = normalizedROI.min(new BigDecimal("100"));

            BigDecimal normalizedInfra = college.getInfrastructure()
                    .divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            BigDecimal normalizedTeaching = college.getTeachingQuality()
                    .divide(new BigDecimal("5"), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            BigDecimal normalizedReviewCount = BigDecimal.valueOf(Math.min(college.getReviewCount(), 100));

            BigDecimal weightedScore = normalizedRating.multiply(REVIEW_WEIGHT)
                    .add(normalizedPlacement.multiply(PLACEMENT_WEIGHT))
                    .add(normalizedROI.multiply(ROI_WEIGHT))
                    .add(normalizedInfra.multiply(INFRA_WEIGHT))
                    .add(normalizedTeaching.multiply(TEACHING_WEIGHT))
                    .add(normalizedReviewCount.multiply(BigDecimal.valueOf(0.05)));

            scores.put(college.getCollege().getName(), weightedScore);
        }

        return scores;
    }

    private void rankColleges(List<CollegeComparisonData> data, Map<String, BigDecimal> scores) {
        data.sort((a, b) -> scores.get(b.getCollege().getName())
                .compareTo(scores.get(a.getCollege().getName())));

        for (int i = 0; i < data.size(); i++) {
            data.get(i).setRank(i + 1);
        }
    }

    private List<ComparisonMetric> buildComparisonMetrics(List<CollegeComparisonData> data) {
        List<ComparisonMetric> metrics = new ArrayList<>();

        // Define metrics to compare
        List<MetricDefinition> metricDefs = List.of(
                new MetricDefinition("Overall Rating", "rating", "⭐", "Student satisfaction score (1-5)", false),
                new MetricDefinition("Review Count", "reviewCount", "📝", "Number of student reviews", false),
                new MetricDefinition("Placement %", "placement", "📊", "Percentage of students placed", false),
                new MetricDefinition("Median Package", "package", "₹ LPA", "Median salary offered", false),
                new MetricDefinition("Total Fees", "fees", "₹ L", "Total course fees", true),
                new MetricDefinition("Teaching Quality", "teaching", "⭐", "Faculty expertise rating (1-5)", false),
                new MetricDefinition("Infrastructure", "infra", "⭐", "Campus facilities rating (1-5)", false),
                new MetricDefinition("Hostel Life", "hostel", "⭐", "Accommodation quality rating (1-5)", false),
                new MetricDefinition("Campus Life", "campus", "⭐", "Student life rating (1-5)", false),
                new MetricDefinition("Value for Money", "value", "⭐", "Fees vs experience rating (1-5)", false),
                new MetricDefinition("Would Recommend", "recommend", "%", "Students who recommend", false),
                new MetricDefinition("ROI Score", "roi", "₹ L", "Return on investment over 4 years", false)
        );

        for (MetricDefinition def : metricDefs) {
            List<BigDecimal> values = new ArrayList<>();
            for (CollegeComparisonData college : data) {
                BigDecimal value = getMetricValue(college, def.key);
                values.add(value);
            }

            // Determine winner
            BigDecimal bestValue = def.lowerIsBetter ?
                    values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO) :
                    values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            int winnerIndex = values.indexOf(bestValue);
            String winner = winnerIndex >= 0 ? data.get(winnerIndex).getCollege().getName() : "Tie";

            metrics.add(ComparisonMetric.builder()
                    .metricName(def.name)
                    .metricKey(def.key)
                    .unit(def.unit)
                    .college1Value(values.size() > 0 ? values.get(0) : BigDecimal.ZERO)
                    .college2Value(values.size() > 1 ? values.get(1) : BigDecimal.ZERO)
                    .college3Value(values.size() > 2 ? values.get(2) : BigDecimal.ZERO)
                    .college4Value(values.size() > 3 ? values.get(3) : BigDecimal.ZERO)
                    .winner(winner)
                    .tooltip(def.tooltip)
                    .build());
        }

        return metrics;
    }

    private BigDecimal getMetricValue(CollegeComparisonData college, String metric) {
        switch (metric) {
            case "rating": return college.getOverallRating();
            case "reviewCount": return BigDecimal.valueOf(college.getReviewCount());
            case "placement": return college.getPlacementPercentage() != null ?
                    college.getPlacementPercentage() : BigDecimal.ZERO;
            case "package": return college.getMedianPackage() != null ?
                    college.getMedianPackage().divide(new BigDecimal("100000"), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            case "fees": return college.getTotalFees() != null ?
                    college.getTotalFees().divide(new BigDecimal("100000"), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            case "teaching": return college.getTeachingQuality();
            case "infra": return college.getInfrastructure();
            case "hostel": return college.getHostelLife();
            case "campus": return college.getCampusLife();
            case "value": return college.getValueForMoney();
            case "recommend": return BigDecimal.valueOf(college.getWouldRecommendPercent());
            case "roi": return college.getRoiScore().divide(new BigDecimal("100000"), 1, RoundingMode.HALF_UP);
            default: return BigDecimal.ZERO;
        }
    }

    private ROIComparison calculateROIComparison(List<CollegeComparisonData> data) {
        List<ROIData> rois = new ArrayList<>();

        for (CollegeComparisonData college : data) {
            BigDecimal roiPerYear = college.getRoiScore();
            BigDecimal roiPercentage = calculateROIPercentage(college);
            String roiLabel = getROILabel(roiPerYear);

            rois.add(ROIData.builder()
                    .collegeName(college.getCollege().getName())
                    .totalFees(college.getTotalFees())
                    .medianPackage(college.getMedianPackage())
                    .durationYears(4)
                    .roiPerYear(roiPerYear)
                    .roiPercentage(roiPercentage)
                    .roiLabel(roiLabel)
                    .build());
        }

        // Find best ROI (highest return)
        String bestRoiCollege = rois.stream()
                .max(Comparator.comparing(ROIData::getRoiPerYear))
                .map(ROIData::getCollegeName)
                .orElse("N/A");

        // Find best value (highest package/fees ratio)
        String bestValueCollege = data.stream()
                .max(Comparator.comparing(c -> {
                    if (c.getMedianPackage() == null || c.getTotalFees() == null) return BigDecimal.ZERO;
                    return c.getMedianPackage().divide(c.getTotalFees(), 2, RoundingMode.HALF_UP);
                }))
                .map(c -> c.getCollege().getName())
                .orElse("N/A");

        // Find best package (highest median package)
        String bestPackageCollege = data.stream()
                .max(Comparator.comparing(c -> c.getMedianPackage() != null ? c.getMedianPackage() : BigDecimal.ZERO))
                .map(c -> c.getCollege().getName())
                .orElse("N/A");

        return ROIComparison.builder()
                .rois(rois)
                .bestRoiCollege(bestRoiCollege)
                .bestValueCollege(bestValueCollege)
                .bestPackageCollege(bestPackageCollege)
                .build();
    }

    private BigDecimal calculateROIPercentage(CollegeComparisonData college) {
        if (college.getMedianPackage() == null || college.getTotalFees() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal roiPercent = college.getMedianPackage()
                .multiply(new BigDecimal("100"))
                .divide(college.getTotalFees(), 1, RoundingMode.HALF_UP);
        return roiPercent;
    }

    private String getROILabel(BigDecimal roiPerYear) {
        if (roiPerYear.compareTo(new BigDecimal("2000000")) >= 0) return "Excellent";
        if (roiPerYear.compareTo(new BigDecimal("1000000")) >= 0) return "Good";
        if (roiPerYear.compareTo(new BigDecimal("500000")) >= 0) return "Fair";
        return "Poor";
    }

    private String generateRecommendation(List<CollegeComparisonData> data, Map<String, BigDecimal> scores) {
        CollegeComparisonData bestOverall = data.get(0);
        CollegeComparisonData bestValue = data.stream()
                .max(Comparator.comparing(c -> {
                    if (c.getMedianPackage() == null || c.getTotalFees() == null) return BigDecimal.ZERO;
                    return c.getMedianPackage().divide(c.getTotalFees(), 2, RoundingMode.HALF_UP);
                }))
                .orElse(data.get(0));

        StringBuilder recommendation = new StringBuilder();
        recommendation.append("📊 **Analysis Summary**\n\n");
        recommendation.append("🏆 **Best Overall**: ").append(bestOverall.getCollege().getName())
                .append(" (Score: ").append(scores.get(bestOverall.getCollege().getName()).intValue())
                .append("/100)\n");
        recommendation.append("💰 **Best Value for Money**: ").append(bestValue.getCollege().getName())
                .append("\n\n");

        recommendation.append("📈 **Key Insights**:\n");
        recommendation.append("• ").append(bestOverall.getCollege().getName())
                .append(" leads in ").append(getLeadingMetrics(data, bestOverall.getCollege().getName()))
                .append("\n");

        if (!bestOverall.getCollege().getName().equals(bestValue.getCollege().getName())) {
            recommendation.append("• ").append(bestValue.getCollege().getName())
                    .append(" offers better ROI with ")
                    .append(bestValue.getTotalFees() != null ?
                            "₹" + bestValue.getTotalFees().divide(new BigDecimal("100000"), 0, RoundingMode.HALF_UP) + "L fees" : "lower fees")
                    .append("\n");
        }

        return recommendation.toString();
    }

    private String getLeadingMetrics(List<CollegeComparisonData> data, String collegeName) {
        List<String> leadingMetrics = new ArrayList<>();

        CollegeComparisonData target = data.stream()
                .filter(c -> c.getCollege().getName().equals(collegeName))
                .findFirst().orElse(null);

        if (target == null) return "multiple metrics";

        for (CollegeComparisonData other : data) {
            if (other.getCollege().getName().equals(collegeName)) continue;

            if (target.getOverallRating().compareTo(other.getOverallRating()) > 0)
                leadingMetrics.add("ratings");
            if (target.getPlacementPercentage() != null && other.getPlacementPercentage() != null &&
                    target.getPlacementPercentage().compareTo(other.getPlacementPercentage()) > 0)
                leadingMetrics.add("placements");
            if (target.getRoiScore().compareTo(other.getRoiScore()) > 0)
                leadingMetrics.add("ROI");
            break;
        }

        return leadingMetrics.isEmpty() ? "overall performance" :
                String.join(", ", leadingMetrics.stream().limit(2).collect(Collectors.toList()));
    }

    @Transactional
    public SavedComparison saveComparison(User user, String name, List<UUID> collegeIds, UUID programId) {
        SavedComparison saved = new SavedComparison();
        saved.setUser(user);
        saved.setName(name);
        saved.setCollegeIds(collegeIds.toArray(new UUID[0]));

        var program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found"));
        saved.setProgram(program);

        return savedComparisonRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public Page<SavedComparison> getUserSavedComparisons(User user, Pageable pageable) {
        return savedComparisonRepository.findByUser(user, pageable);
    }

    @Transactional
    public void deleteSavedComparison(User user, UUID comparisonId) {
        savedComparisonRepository.deleteByUserAndId(user, comparisonId);
    }

    // Inner class for metric definition
    private static class MetricDefinition {
        String name;
        String key;
        String unit;
        String tooltip;
        boolean lowerIsBetter;

        MetricDefinition(String name, String key, String unit, String tooltip, boolean lowerIsBetter) {
            this.name = name;
            this.key = key;
            this.unit = unit;
            this.tooltip = tooltip;
            this.lowerIsBetter = lowerIsBetter;
        }
    }
}